package com.scaffold.admin;

import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.RemoteRef;
import com.scaffold.lowcode.workspace.ScaffoldSnapshotWriter;
import com.scaffold.lowcode.workspace.ScaffoldTrimPolicy;
import com.scaffold.lowcode.workspace.WorkspaceGitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目仓库与工作区链路（PR 4）的状态机与守卫。
 *
 * <p>用一台可控的「假远端」跑：JGit 的真实语义由 {@code JGitGatewayTest} 对本机裸仓库验证，
 * 这里要验的是平台在成功 / 不可达 / 未推送 / 重复初始化这些分支上怎么推进 {@code sync_status}
 * 与 {@code sync_message}，以及固定代码是不是真的按裁剪清单落了盘。
 */
@SpringBootTest(
        classes = {ScaffoldAdminApplication.class, ProjectWorkspaceApiTest.StubGitConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "scaffold.lowcode.workspace-root=${java.io.tmpdir}/scaffold-it-workspace",
        "scaffold.lowcode.scaffold-source-root="
})
class ProjectWorkspaceApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String REPO = "git@github.com:acme/order-service.git";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubGitGateway gateway;

    @AfterEach
    void resetStub() {
        gateway.reset();
    }

    @Test
    void 空仓库初始化后状态就绪且固定代码按清单落盘() {
        String admin = platformToken();
        String code = newCode("itinit");
        long id = createProject(admin, code, REPO);
        try {
            gateway.remoteRefs = List.of();
            ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> data = dataOf(response.getBody());
            assertThat(data).containsEntry("syncStatus", 2);
            assertThat(String.valueOf(data.get("commitSha"))).hasSize(40);

            Map<String, Object> row = projectRow(id);
            assertThat(row).containsEntry("sync_status", 2);
            assertThat(String.valueOf(row.get("sync_message"))).contains("固定代码已推送到");
            assertThat(String.valueOf(row.get("workspace_path"))).isEqualTo(workspaceRelative(code));
            assertThat(String.valueOf(row.get("last_commit_sha"))).isEqualTo(String.valueOf(data.get("commitSha")));
            assertThat(row.get("init_time")).isNotNull();
            assertThat(String.valueOf(row.get("base_scaffold_sha"))).hasSize(40);

            // 真落了盘：README 在，平台自己的模块不在
            Path workspace = workspaceRoot().resolve(workspaceRelative(code));
            assertThat(Files.isRegularFile(workspace.resolve("README.md"))).isTrue();
            assertThat(Files.isRegularFile(workspace.resolve(ScaffoldTrimPolicy.MARKER_FILE))).isTrue();
            assertThat(Files.isDirectory(workspace.resolve("scaffold-system/scaffold-lowcode"))).isFalse();
            assertThat(Files.isDirectory(workspace.resolve("scaffold-vue/src/views/tool"))).isFalse();

            // 推上去的就是发布分支，主干没被平台写过
            assertThat(gateway.pushedBranches).containsExactly("platform-generated");
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 非空仓库全量初始化会清掉快照外的旧物料文件() {
        String admin = platformToken();
        String code = newCode("itlegacy");
        long id = createProject(admin, code, REPO);
        try {
            // 远端已有主干与发布分支，clone 出来的是「抽包前的旧形态」：组件源码被拷在项目里
            gateway.remoteRefs = List.of(new RemoteRef("refs/heads/main", "1".repeat(40)),
                    new RemoteRef("refs/heads/platform-generated", "2".repeat(40)));
            gateway.legacyFormOnClone = true;

            ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(dataOf(response.getBody())).containsEntry("syncStatus", 2);

            Path workspace = workspaceRoot().resolve(workspaceRelative(code));
            // 快照内的东西在
            assertThat(Files.isRegularFile(workspace.resolve("README.md"))).isTrue();
            assertThat(Files.isRegularFile(workspace.resolve("scaffold-vue/src/components/layout/AppShell.vue"))).isTrue();
            // 快照外的旧拷贝必须一起删干净，否则项目里两份物料并存（抽包就白做了）
            assertThat(Files.isRegularFile(workspace.resolve("scaffold-vue/src/components/business/ListPage.vue"))).isFalse();
            assertThat(Files.isDirectory(workspace.resolve("scaffold-vue/src/components/business"))).isFalse();
            assertThat(String.valueOf(projectRow(id).get("sync_message"))).contains("清理快照外旧文件");
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 仓库不可达时状态与原因都写进项目() {
        String admin = platformToken();
        String code = newCode("itreach");
        long id = createProject(admin, code, REPO);
        try {
            gateway.transportFailure = new WorkspaceGitException(5, "读取远端仓库失败：" + REPO + " → 连接被拒绝");
            ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(response)).contains("连接被拒绝");

            Map<String, Object> row = projectRow(id);
            assertThat(row).containsEntry("sync_status", 5);
            assertThat(String.valueOf(row.get("sync_message"))).contains("连接被拒绝");
            // 失败原因同时出现在列表接口上，人不用去翻日志
            assertThat(String.valueOf(findProject(admin, code).get("syncMessage"))).contains("连接被拒绝");
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 库里存着非法仓库地址时初始化当场拒绝() {
        String admin = platformToken();
        String code = newCode("itbadurl");
        long id = createProject(admin, code, "https://github.com/acme/ok.git");
        try {
            // 绕过接口直接写库，模拟历史脏数据或人工改库：接口层白名单挡不住这种来源
            jdbcTemplate.update("UPDATE lc_project SET repo_url = 'file:///tmp/whatever.git' WHERE id = ?", id);
            ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(response)).contains("只支持 https:// 或 git@");
            assertThat(projectRow(id)).containsEntry("sync_status", 0);
            assertThat(gateway.pushedBranches).isEmpty();
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 已就绪的项目不会被再次初始化覆盖() {
        String admin = platformToken();
        String code = newCode("itagain");
        long id = createProject(admin, code, REPO);
        try {
            gateway.remoteRefs = List.of();
            assertThat(post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<Map<String, Object>> second = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(second)).contains("平台不覆盖 A 档文件");
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 有未推送提交时禁止重建工作区() {
        String admin = platformToken();
        String code = newCode("itunpushed");
        long id = createProject(admin, code, REPO);
        try {
            gateway.remoteRefs = List.of();
            assertThat(post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            // 本地分支上有提交、远端没有对应提交
            gateway.remoteRefs = List.of();
            gateway.localShaOverride = "0123456789abcdef0123456789abcdef01234567";

            ResponseEntity<Map<String, Object>> response = delete("/api/lowcode/projects/" + id + "/workspace", admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(response)).contains("未推送提交");
            assertThat(Files.isDirectory(workspaceRoot().resolve(workspaceRelative(code)))).isTrue();
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 工作区目录丢失时清空动作把状态重置回未初始化() {
        String admin = platformToken();
        String code = newCode("itlost");
        long id = createProject(admin, code, REPO);
        try {
            gateway.remoteRefs = List.of();
            assertThat(post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            Path workspace = workspaceRoot().resolve(workspaceRelative(code));
            // 换服务器 / 清过卷 / 手工删过目录：远端内容还在，但本地工作区没了
            ScaffoldSnapshotWriter.deleteWorkspace(workspace);
            assertThat(Files.isDirectory(workspace)).isFalse();
            assertThat(projectRow(id)).containsEntry("sync_status", 2);

            ResponseEntity<Map<String, Object>> response = delete("/api/lowcode/projects/" + id + "/workspace", admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> row = projectRow(id);
            assertThat(row).containsEntry("sync_status", 0);
            assertThat(String.valueOf(row.get("sync_message"))).contains("状态已重置");

            // 重置过一次之后再来一次才是真的无事可做
            ResponseEntity<Map<String, Object>> again = delete("/api/lowcode/projects/" + id + "/workspace", admin);
            assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(messageOf(again)).contains("无需重建");

            // 状态回到未初始化后，初始化入口重新可用（远端非空走全量同步分支，仓库要认得出脚手架）
            gateway.legacyFormOnClone = true;
            gateway.remoteRefs = List.of(new RemoteRef("refs/heads/main", "1".repeat(40)));
            assertThat(post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(Files.isRegularFile(workspace.resolve("README.md"))).isTrue();
            assertThat(Files.isRegularFile(workspace.resolve("scaffold-vue/src/components/business/ListPage.vue"))).isFalse();
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 工作区状态读数能区分脏与未推送() {
        String admin = platformToken();
        String code = newCode("itstatus");
        long id = createProject(admin, code, REPO);
        try {
            Map<String, Object> before = dataOf(get("/api/lowcode/projects/" + id + "/workspace", admin));
            assertThat(before).containsEntry("exists", false);
            assertThat(before).containsEntry("syncStatusText", "未初始化");

            gateway.remoteRefs = List.of();
            assertThat(post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin).getStatusCode()).isEqualTo(HttpStatus.OK);

            gateway.localShaOverride = null;
            gateway.dirty = true;
            Map<String, Object> after = dataOf(get("/api/lowcode/projects/" + id + "/workspace", admin));
            assertThat(after).containsEntry("exists", true);
            assertThat(after).containsEntry("dirty", true);
            assertThat(after).containsEntry("syncStatusText", "就绪");
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 连通性检查只读远端不改项目状态() {
        String admin = platformToken();
        String code = newCode("itcheck");
        long id = createProject(admin, code, REPO);
        try {
            gateway.remoteRefs = List.of(new RemoteRef("refs/heads/main", "1".repeat(40)),
                    new RemoteRef("refs/heads/release", "2".repeat(40)));
            Map<String, Object> data = dataOf(post("/api/lowcode/projects/" + id + "/repo/check", Map.of(), admin).getBody());
            assertThat(data).containsEntry("reachable", true)
                    .containsEntry("empty", false)
                    .containsEntry("mainBranchExists", true)
                    .containsEntry("genBranchExists", false);
            assertThat(String.valueOf(data.get("action"))).contains("初始化把固定代码推到发布分支");
            // 检查失败或成功都不该动生命周期
            assertThat(projectRow(id)).containsEntry("sync_status", 0);

            gateway.transportFailure = new WorkspaceGitException(5, "读取远端仓库失败：" + REPO + " → 主机指纹未登记");
            Map<String, Object> failed = dataOf(post("/api/lowcode/projects/" + id + "/repo/check", Map.of(), admin).getBody());
            assertThat(failed).containsEntry("reachable", false);
            assertThat(String.valueOf(failed.get("message"))).contains("主机指纹未登记");
            assertThat(projectRow(id)).containsEntry("sync_status", 0);
        } finally {
            cleanup(code, id);
        }
    }

    @Test
    void 停用中的项目不允许初始化() {
        String admin = platformToken();
        String code = newCode("itpaused");
        long id = createProject(admin, code, REPO);
        try {
            jdbcTemplate.update("UPDATE lc_project SET status = 0 WHERE id = ?", id);
            ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects/" + id + "/initialize", Map.of(), admin);
            assertThat(messageOf(response)).contains("项目已停用");
        } finally {
            cleanup(code, id);
        }
    }

    private long createProject(String token, String code, String repoUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectCode", code);
        body.put("projectName", "工作区验收");
        body.put("repoUrl", repoUrl);
        body.put("mainBranch", "main");
        body.put("genBranch", "platform-generated");
        ResponseEntity<Map<String, Object>> response = post("/api/lowcode/projects", body, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) dataOf(response.getBody()).get("id")).longValue();
    }

    private Map<String, Object> projectRow(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT sync_status, sync_message, workspace_path, base_scaffold_sha, last_commit_sha, init_time, status"
                        + " FROM lc_project WHERE id = ?", id);
        return row;
    }

    private Map<String, Object> findProject(String token, String projectCode) {
        return rowsOf(dataOf(get("/api/lowcode/projects?pageNum=1&pageSize=100&keyword=" + projectCode, token))).stream()
                .filter(row -> projectCode.equals(String.valueOf(row.get("projectCode"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("查不到项目 " + projectCode));
    }

    private static Path workspaceRoot() {
        return Path.of(System.getProperty("java.io.tmpdir"), "scaffold-it-workspace");
    }

    private static String workspaceRelative(String projectCode) {
        // 平台租户固定是 1，工作区相对路径是 {tenantId}/{projectCode}
        return "1/" + projectCode;
    }

    private static String newCode(String prefix) {
        return prefix + System.nanoTime() % 1_000_000;
    }

    private void cleanup(String code, long id) {
        try {
            Path workspace = workspaceRoot().resolve(workspaceRelative(code));
            if (Files.isDirectory(workspace)) {
                try (var walk = Files.walk(workspace)) {
                    for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        } catch (Exception ignored) {
            // 工作区残留不影响断言结论，下次跑前会重建
        }
        jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", id);
    }

    private String platformToken() {
        return TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    private static List<Map<String, Object>> rowsOf(Map<String, Object> data) {
        Object rows = data.get("rows");
        assertThat(rows).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) rows;
        return list;
    }

    private static Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).isNotNull();
        Object data = body.get("data");
        assertThat(data).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) data;
        return map;
    }

    private static String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return String.valueOf(response.getBody().get("message"));
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(token)), MAP_TYPE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    private ResponseEntity<Map<String, Object>> delete(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), MAP_TYPE);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** 可控远端：引用列表、提交号、脏与不可达都由测试摆布，真实 JGit 语义在 JGitGatewayTest 里验 */
    @TestConfiguration
    static class StubGitConfig {
        @Bean
        @Primary
        GitGateway stubGitGateway() {
            return new StubGitGateway();
        }
    }

    static class StubGitGateway implements GitGateway {

        List<RemoteRef> remoteRefs = new ArrayList<>();
        WorkspaceGitException transportFailure;
        String localShaOverride;
        boolean dirty;
        /** clone 出来的仓库按「抽包前的旧形态」铺几个文件，用来验全量初始化会清理快照外的遗留 */
        boolean legacyFormOnClone;
        final List<String> pushedBranches = new ArrayList<>();

        void reset() {
            remoteRefs = new ArrayList<>();
            transportFailure = null;
            localShaOverride = null;
            dirty = false;
            legacyFormOnClone = false;
            pushedBranches.clear();
        }

        @Override
        public List<RemoteRef> lsRemote(String repoUrl) {
            if (transportFailure != null) {
                throw transportFailure;
            }
            return List.copyOf(remoteRefs);
        }

        @Override
        public void initEmpty(Path dir, String repoUrl, String initialBranch) {
            touchRepo(dir);
        }

        @Override
        public void cloneBranch(Path dir, String repoUrl, String branch) {
            touchRepo(dir);
            if (legacyFormOnClone) {
                // 模拟抽包之前推出去的旧形态：认得出脚手架（marker），但组件源码是拷进项目的
                try {
                    Files.createDirectories(dir.resolve("scaffold-vue/src/components/business"));
                    Files.writeString(dir.resolve("scaffold-vue/src/components/business/ListPage.vue"), "<template>旧拷贝</template>");
                    Files.writeString(dir.resolve(ScaffoldTrimPolicy.MARKER_FILE), "{\"projectCode\":\"x\"}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        /** 假远端只补一个 .git 目录，让「工作区是不是已就绪的仓库」这个判断在测试里也成立 */
        private void touchRepo(Path dir) {
            try {
                Files.createDirectories(dir.resolve(".git"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void checkoutBranch(Path dir, String branch, String startPoint) {
            // 同上
        }

        @Override
        public String commitAll(Path dir, String message) {
            return localShaOverride != null ? localShaOverride : "a".repeat(40);
        }

        @Override
        public void pushBranch(Path dir, String branch) {
            if (transportFailure != null) {
                throw transportFailure;
            }
            pushedBranches.add(branch);
            String sha = localShaOverride != null ? localShaOverride : "a".repeat(40);
            remoteRefs = withBranch(remoteRefs, branch, sha);
        }

        @Override
        public boolean isDirty(Path dir) {
            return dirty;
        }

        @Override
        public String localBranchSha(Path dir, String branch) {
            return localShaOverride != null ? localShaOverride : remoteSha(branch);
        }

        @Override
        public boolean isValidBranch(String branch) {
            return branch != null && !branch.isBlank() && !branch.contains("..");
        }

        @Override
        public String headSha(Path repoDir) {
            return "b".repeat(40);
        }

        private String remoteSha(String branch) {
            return remoteRefs.stream().filter(ref -> branch.equals(ref.branchName())).map(RemoteRef::sha).findFirst().orElse(null);
        }

        private static List<RemoteRef> withBranch(List<RemoteRef> refs, String branch, String sha) {
            List<RemoteRef> next = new ArrayList<>(refs);
            next.removeIf(ref -> branch.equals(ref.branchName()));
            if (sha != null) {
                next.add(new RemoteRef("refs/heads/" + branch, sha));
            }
            return next;
        }
    }
}
