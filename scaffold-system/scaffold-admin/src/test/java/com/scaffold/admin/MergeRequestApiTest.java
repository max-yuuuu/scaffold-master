package com.scaffold.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合并请求的只读那一半（PR 8 第一刀，方案 §5.4）。
 *
 * <p>远端用 {@link LocalBareGitGateway}（真 JGit 打在本机裸仓库上），不用 {@code StubGitGateway}：
 * 这一刀的验收四条全部是仓库语义 ——「清单只含源分支自己的改动」「冲突场景只列文件不自动取舍」
 * 「目标分支前进后旧请求自动转陈旧」「全程不写主干」。桩只能证明平台自己怎么记账，
 * 证不了那两个提交之间真有什么。
 *
 * <p>{@code JGitGatewayTest} 验的是单条原语各自的语义，这里验它们接起来、落库、走接口之后，
 * 人在页面上看到的那几个数与那句话。
 */
@SpringBootTest(
        classes = {ScaffoldAdminApplication.class, LocalBareGitGateway.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "scaffold.lowcode.workspace-root=${java.io.tmpdir}/scaffold-it-merge",
        "scaffold.lowcode.scaffold-source-root="
})
class MergeRequestApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String MAIN = "main";
    private static final String GEN = "platform-generated";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LocalBareGitGateway gateway;

    @TempDir
    Path tempDir;

    private final List<Harness> created = new ArrayList<>();
    private String token;

    @BeforeEach
    void login() {
        token = TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    @AfterEach
    void cleanUp() {
        created.forEach(Harness::clean);
        created.clear();
    }

    @Test
    void 主干与发布分支没有共同祖先时拒绝并说清原因() {
        // 真造两条独立历史：平台先把固定代码推成 gen，仓库侧自己再起一条没有关联的 main。
        // 注意「先 seed main 再初始化」不算这种状态 —— 那时平台是从已有历史 clone 出去的，
        // gen 是 main 的后代，第一次刷新本来就该报出整个快照的文件数（那是对的，不是缺陷）
        Harness fx = new Harness(false);
        Path stray = tempDir.resolve("stray");
        gateway.initEmpty(stray, fx.repoUrl, MAIN);
        write(stray.resolve("README.md"), "仓库侧自己起的头\n");
        assertThat(gateway.commitAll(stray, "chore: 与发布分支无关的主干")).isNotBlank();
        gateway.pushBranch(stray, MAIN);

        Map<String, Object> body = post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)).getBody();

        assertThat(body).as(String.valueOf(body)).containsEntry("code", 400);
        assertThat(String.valueOf(body.get("message"))).contains("没有共同祖先");
        assertThat(requestRows(fx.projectId)).isZero();
        deleteRecursively(stray);
    }

    @Test
    void 远端还没有主干时拒绝而不是给一条空清单() {
        Harness fx = new Harness(false);
        Map<String, Object> body = post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)).getBody();

        assertThat(body).as(String.valueOf(body)).containsEntry("code", 400);
        assertThat(String.valueOf(body.get("message"))).contains("主干 " + MAIN);
        assertThat(requestRows(fx.projectId)).isZero();
    }

    @Test
    void 清单只含发布分支自己的改动而主干的不出现() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);

        // 主干上人的改动：这次合并要带进主干的东西里没有它，反向算成「要删掉它」更是错的
        gateway.humanEditAndPush(fx.repoUrl, MAIN, "README.md", before -> "人工补的说明\n");
        String mainBeforeRefresh = gateway.branchSha(fx.repoUrl, MAIN);
        // 发布分支上新生出来的东西：这才是「这次要合进主干的内容」
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");

        Map<String, Object> data = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));

        assertThat(castList(data.get("files"))).extracting(row -> row.get("path")).containsExactly("GenPage.vue");
        assertThat(number(data.get("fileCount"))).isEqualTo(1);
        assertThat(String.valueOf(data.get("title"))).contains("1 个文件待合并");
        assertThat(castList(data.get("files")).get(0).get("changeType")).isEqualTo("ADD");
        assertThat(castList(data.get("files")).get(0).get("oldPath")).isNull();
        assertThat(number(data.get("checkStatus"))).isEqualTo(1);
        assertThat(String.valueOf(data.get("checkStatusText"))).isEqualTo("可自动合并");
        assertThat(number(data.get("conflictCount"))).isZero();
        // 分叉点必须真的是那个共同提交：按 主干..发布分支 算就会把人的改动反向算进来
        assertThat(String.valueOf(data.get("mergeBaseSha"))).isEqualTo(fx.snapshotSha);
        // 只算不合：主干还停在人自己那个提交上，一个提交号都没变
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(mainBeforeRefresh);
        assertThat(requestRows(fx.projectId)).isEqualTo(1L);

        // 列表那一条也自带文案与那两个数：状态词的口径只在后端 text() 一处，
        // 让前端自己拿数字去猜词，两处就会开始各说各话。分页沿用底座那套 PageResult，不是裸数组
        Map<String, Object> page = castMap(get("/api/lowcode/merge-requests?projectId=" + fx.projectId
                + "&pageNum=1&pageSize=20").get("data"));
        assertThat(number(page.get("total"))).isEqualTo(1);
        List<Map<String, Object>> rows = castList(page.get("rows"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("title")).isEqualTo(data.get("title"));
        assertThat(number(rows.get(0).get("fileCount"))).isEqualTo(1);
        assertThat(number(rows.get(0).get("conflictCount"))).isZero();
        assertThat(String.valueOf(rows.get(0).get("statusText"))).isEqualTo("待审");
        assertThat(String.valueOf(rows.get(0).get("checkStatusText"))).isEqualTo("可自动合并");
    }

    @Test
    void 两边改了同一行要报冲突且只列文件不自动取舍() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        gateway.humanEditAndPush(fx.repoUrl, MAIN, "README.md", before -> "主干这一行是人的\n");
        commitOnGen(fx, "README.md", "发布分支这一行是生成的\n");
        String mainSha = gateway.branchSha(fx.repoUrl, MAIN);

        Map<String, Object> data = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));

        assertThat(number(data.get("checkStatus"))).isEqualTo(2);
        assertThat(String.valueOf(data.get("checkStatusText"))).isEqualTo("有冲突");
        assertThat(number(data.get("conflictCount"))).isEqualTo(1);
        List<Map<String, Object>> files = castList(data.get("files"));
        // 冲突那一行排在最前；话只说到「哪个文件」，谁赢不替人决定
        assertThat(files.get(0).get("path")).isEqualTo("README.md");
        assertThat(number(files.get(0).get("isConflict"))).isEqualTo(1);
        assertThat(String.valueOf(data.get("checkMessage"))).contains("README.md");
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(mainSha);
    }

    @Test
    void 主干又前进一次后详情把这条请求转成陈旧() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");

        Map<String, Object> data = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));
        long id = number(data.get("id"));
        assertThat(number(data.get("status"))).isZero();
        assertThat(String.valueOf(data.get("statusText"))).isEqualTo("待审");

        gateway.humanEditAndPush(fx.repoUrl, MAIN, "README.md", before -> "晚来的一笔\n");

        Map<String, Object> again = dataOf(get("/api/lowcode/merge-requests/" + id));
        assertThat(number(again.get("status"))).isEqualTo(1);
        assertThat(String.valueOf(again.get("statusText"))).isEqualTo("陈旧");
        assertThat(String.valueOf(again.get("checkMessage"))).contains("已前进");
        // 陈旧只改状态：那三个提交号还是这条请求当时算的那一对，改了它们这句话就不成立了
        assertThat(String.valueOf(again.get("targetSha"))).isEqualTo(data.get("targetSha"));
        assertThat(String.valueOf(again.get("sourceSha"))).isEqualTo(data.get("sourceSha"));
        assertThat(String.valueOf(again.get("mergeBaseSha"))).isEqualTo(data.get("mergeBaseSha"));
    }

    @Test
    void 重算两次覆盖同一条请求而不是越刷越多() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");

        long first = number(dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId))).get("id"));
        Map<String, Object> second = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));

        assertThat(number(second.get("id"))).isEqualTo(first);
        assertThat(requestRows(fx.projectId)).isEqualTo(1L);
        // 清单是整批替换：内容没变就不该攒出第二批行，否则「这次要合什么」会有两份互相矛盾的答案
        assertThat(fileRows(first)).isEqualTo(1L);
        assertThat(number(second.get("fileCount"))).isEqualTo(1);
    }

    /* ── 第二刀：负责人审核与合并执行 ── */

    @Test
    void 负责人合并后主干真的前进并记下谁合的() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        setOwner(fx, ADMIN_USER);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");
        Map<String, Object> request = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));
        String mainBefore = gateway.branchSha(fx.repoUrl, MAIN);

        Map<String, Object> merged = dataOf(post("/api/lowcode/merge-requests/" + number(request.get("id"))
                + "/merge", Map.of()));

        assertThat(String.valueOf(merged.get("statusText"))).isEqualTo("已合并");
        assertThat(String.valueOf(merged.get("mergedBy"))).isEqualTo("admin");
        assertThat(merged.get("mergedTime")).isNotNull();
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isNotEqualTo(mainBefore);
        // 主干上真的有了发布分支那个文件：合并不是只改了库里的状态
        Path reader = tempDir.resolve("verify-" + System.nanoTime());
        gateway.cloneBranch(reader, fx.repoUrl, MAIN);
        assertThat(reader.resolve("GenPage.vue")).exists();
        assertThat(reader.resolve("README.md")).exists();
        deleteRecursively(reader);
    }

    @Test
    void 预检有冲突时合并被拒并且主干一个提交都没多() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        setOwner(fx, ADMIN_USER);
        gateway.humanEditAndPush(fx.repoUrl, MAIN, "README.md", before -> "主干这一行是人的\n");
        commitOnGen(fx, "README.md", "发布分支这一行是生成的\n");
        Map<String, Object> request = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));
        String mainBefore = gateway.branchSha(fx.repoUrl, MAIN);

        Map<String, Object> body = post("/api/lowcode/merge-requests/" + number(request.get("id")) + "/merge", Map.of()).getBody();

        assertThat(body).as(String.valueOf(body)).containsEntry("code", 400);
        assertThat(String.valueOf(body.get("message"))).contains("冲突").contains("人解");
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(mainBefore);
    }

    @Test
    void 提交之后主干又前进时合并被拒而不是合到错的内容上() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        setOwner(fx, ADMIN_USER);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");
        Map<String, Object> request = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));

        // 预检之后主干又来一笔：人点的这条请求算的是旧的那一对提交
        gateway.humanEditAndPush(fx.repoUrl, MAIN, "README.md", before -> "晚来的一笔\n");

        Map<String, Object> body = post("/api/lowcode/merge-requests/" + number(request.get("id")) + "/merge", Map.of()).getBody();

        assertThat(body).as(String.valueOf(body)).containsEntry("code", 400);
        assertThat(String.valueOf(body.get("message"))).contains("已前进");
    }

    @Test
    void 按文件能取到真实补丁而没改状态时列表只读到待审() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");
        Map<String, Object> request = dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));
        long id = number(request.get("id"));

        Map<String, Object> diff = dataOf(get("/api/lowcode/merge-requests/" + id + "/file-diff?path=GenPage.vue"));
        assertThat(String.valueOf(diff.get("diff"))).contains("@@", "+<template>订单</template>");
        // 不在改动里的文件给空串而不是报错：前端展开一行不该看到一条 400
        assertThat(String.valueOf(dataOf(get("/api/lowcode/merge-requests/" + id + "/file-diff?path=nope.txt")).get("diff"))).isEmpty();
        assertThat(String.valueOf(detailOf(id).get("statusText"))).isEqualTo("待审");
    }

    @Test
    void 待我审核按项目负责人过滤而不是让人挑花眼() {
        Harness fx = new Harness(true);
        rebaseMainOntoSnapshot(fx);
        commitOnGen(fx, "GenPage.vue", "<template>订单</template>\n");
        dataOf(post("/api/lowcode/merge-requests/refresh", Map.of("projectId", fx.projectId)));

        // 三条断言一律按项目来数：本机 dev 后端与 IT 用例共用同一个库（.env.local 里 DB_PORT=3316），
        // 真机验证写下的那条请求（order-serive，id=26）就在这张表里。断言全局 total 等于几，
        // 量的其实是「这台机器上今天有没有人真发过请求」，跟这条验收没关系
        String mine = "/api/lowcode/merge-requests?projectId=" + fx.projectId + "&onlyMine=true";
        setOwner(fx, ADMIN_USER);
        assertThat(number(castMap(get(mine).get("data")).get("total"))).isEqualTo(1);

        setOwner(fx, OTHER_USER);
        assertThat(number(castMap(get(mine).get("data")).get("total"))).isZero();
        // 不限负责人时仍然看得到：审核队列是筛选，不是权限裁剪
        assertThat(number(castMap(get("/api/lowcode/merge-requests?projectId=" + fx.projectId).get("data")).get("total")))
                .isEqualTo(1);
    }

    /** admin 的用户 ID（种子数据）；负责人必须是本租户启用中的账号 */
    private static final long ADMIN_USER = 1L;

    /** 同租户的另一个启用账号，用来验证「待我审核」真的按人过滤 */
    private static final long OTHER_USER = 2L;

    /** 改负责人得连 projectName / repoUrl 一起发（那两个是 @NotBlank）；repoUrl 一定要用项目真实的那个，
     *  随手编一个会把项目的仓库地址覆盖掉，后面的合并就找不到远端了 */
    private void setOwner(Harness fx, long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectName", "合并请求验收");
        body.put("repoUrl", fx.repoUrl);
        body.put("mainBranch", MAIN);
        body.put("genBranch", GEN);
        body.put("ownerUserId", userId);
        assertThat(put("/api/lowcode/projects/" + fx.projectId, body).getBody()).as(String.valueOf(body))
                .containsEntry("code", 200);
    }

    private Map<String, Object> detailOf(long id) {
        return dataOf(get("/api/lowcode/merge-requests/" + id));
    }

    /* ── 现场 ── */

    private final class Harness {
        private final String projectCode;
        private final String repoUrl;
        private final long projectId;
        private final String snapshotSha;

        private Harness(boolean seedMain) {
            created.add(this);
            this.projectCode = "mrg" + Math.abs(System.nanoTime() % 100_000_000);
            this.repoUrl = "git@github.com:acme/" + projectCode + ".git";
            if (seedMain) {
                gateway.seedMainBranch(repoUrl, projectCode);
            }
            Map<String, Object> project = new LinkedHashMap<>();
            project.put("projectCode", projectCode);
            project.put("projectName", "合并请求验收");
            project.put("repoUrl", repoUrl);
            project.put("mainBranch", MAIN);
            project.put("genBranch", GEN);
            this.projectId = number(dataOf(post("/api/lowcode/projects", project)).get("id"));
            assertThat(post("/api/lowcode/projects/" + projectId + "/initialize", Map.of()).getBody())
                    .as("项目仓库要真初始化才有发布分支").containsEntry("code", 200);
            this.snapshotSha = gateway.branchSha(repoUrl, GEN);
        }

        private Path workspace() {
            return Path.of(System.getProperty("java.io.tmpdir"), "scaffold-it-merge", "1", projectCode);
        }

        private void clean() {
            jdbcTemplate.update("""
                    DELETE FROM lc_merge_request_file WHERE request_id IN
                      (SELECT id FROM lc_merge_request WHERE project_id = ?)
                    """, projectId);
            jdbcTemplate.update("DELETE FROM lc_merge_request WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", projectId);
            deleteRecursively(workspace());
            deleteRecursively(gateway.remoteDirOf(repoUrl));
        }
    }

    /** 把主干挪到固定代码那个提交上：之后两边各自前进，才算得出「分叉之后源侧改了什么」 */
    private void rebaseMainOntoSnapshot(Harness fx) {
        Path scratch = tempDir.resolve("rebase-" + System.nanoTime());
        gateway.cloneBranch(scratch, fx.repoUrl, GEN);
        gateway.checkoutBranch(scratch, MAIN, fx.snapshotSha);
        gateway.pushBranch(scratch, MAIN);
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(fx.snapshotSha);
        deleteRecursively(scratch);
    }

    /** 在发布分支上落一个文件并提交推送：等价于「又发布了一次」，但不走发布链路以省掉建模与生成 */
    private void commitOnGen(Harness fx, String relative, String content) {
        Path scratch = tempDir.resolve("gen-" + System.nanoTime());
        gateway.cloneBranch(scratch, fx.repoUrl, GEN);
        write(scratch.resolve(relative), content);
        assertThat(gateway.commitAll(scratch, "feat: 生成的一份产物")).isNotBlank();
        gateway.pushBranch(scratch, GEN);
        deleteRecursively(scratch);
    }

    /* ── 接口与库 ── */

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body) {
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private ResponseEntity<Map<String, Object>> put(String path, Map<String, Object> body) {
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private Map<String, Object> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(auth()), MAP_TYPE).getBody();
    }

    private HttpHeaders auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Map<String, Object> dataOf(ResponseEntity<Map<String, Object>> response) {
        return dataOf(response.getBody());
    }

    private Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).as(String.valueOf(body)).containsEntry("code", 200);
        return castMap(body.get("data"));
    }

    private long requestRows(long projectId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lc_merge_request WHERE project_id = ?", Long.class, projectId);
        return count == null ? 0L : count;
    }

    private long fileRows(long requestId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lc_merge_request_file WHERE request_id = ?", Long.class, requestId);
        return count == null ? 0L : count;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static int number(Object value) {
        return value instanceof Number counted ? counted.intValue() : 0;
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
