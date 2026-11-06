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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 页面发布链路（PR 7，方案 §5.1）：设计 → 生成 → 落盘 → 提交 → 推送 → tag → 版本记录。
 *
 * <p>链路上没有一段是新写的：生成器（PR 5 / PR 6B）、交付快照（PR 4）、JGit 网关（PR 4）各有各的用例。
 * 这里验的是它们接起来之后才看得见的那四条验收：
 * <ul>
 *   <li><b>重复点发布不产生空提交</b> —— 内容没变就是 no-op，不涨版本、不打第二个 tag；</li>
 *   <li><b>推送失败不留下半成品</b> —— 远端拿不到半个提交，平台也不许记一个「已发布」的版本；
 *       而本地那个提交是真实存在的，重试必须把它推上去，不能再叠一个；</li>
 *   <li><b>tag 与 {@code lc_page_version.commit_sha} 对得上</b> —— tag 是合并与回滚的锚点，
 *       锚点指错提交，PR 9 的三级回滚就是把内容回滚到错的那一份；</li>
 *   <li><b>主干不被写</b> —— 发布只碰发布分支，合并由人在 PR 8 显式发起。</li>
 * </ul>
 *
 * <p>远端用 {@link LocalBareGitGateway}（真 JGit 打在本机裸仓库上），不用 {@code StubGitGateway}：
 * 桩只能证明平台自己怎么记账，证明不了仓库里真有什么。生成物落盘后顺手真起 {@code javac}，
 * 把「设计驱动的产物」与「编得过的产物」钉在同一条链上 —— 这正是 PR 6 那条验收的落点。
 *
 * <p>PR 9 的回滚用例也住在这里，不另开一个类复制一份 250 行的夹具：两条链共用同一个现场
 * （项目 + 主干 + 模型字段 + 设计 + 已初始化的工作区），而复制出去的夹具会各自漂 —— 一边补了守卫
 * 另一边不知道，测试就还在绿。代价是类名比内容窄了一格，{@code 回滚…} 那几条用例的注释里写明它们验的是 §5.4。
 */
@SpringBootTest(
        classes = {ScaffoldAdminApplication.class, LocalBareGitGateway.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "scaffold.lowcode.workspace-root=${java.io.tmpdir}/scaffold-it-publish",
        "scaffold.lowcode.scaffold-source-root="
})
class PagePublishApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    private static final String MAIN = "main";
    private static final String GEN = "platform-generated";
    private static final String MODEL_CODE = "sales_order";
    private static final String PAGE_CODE = "sales_order_list";
    private static final String CLASS_NAME = "SalesOrder";
    private static final String MODULE = "sales_order";
    private static final String BUSINESS_ROOT = "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/";
    private static final String BUSINESS_DIR = BUSINESS_ROOT + MODULE + "/";
    private static final String EXTENSION = BUSINESS_DIR + "service/" + CLASS_NAME + "Extension.java";
    /** 表单入参那一档：设计里撤掉一格，这个文件的内容就会跟着变，文件级回滚拿它当「没被点名的文件」 */
    private static final String WRITE_REQUEST = BUSINESS_DIR + "domain/" + CLASS_NAME + "WriteRequest.java";
    private static final String VIEW = "scaffold-vue/src/views/" + MODULE + "/" + CLASS_NAME + ".vue";
    /** 11 份产物：7 个 java + 接口 ts + 列表页 vue + 建表 SQL + 菜单 SQL */
    private static final int FILE_COUNT = 11;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LocalBareGitGateway gateway;

    @TempDir
    Path tempDir;

    private String token;
    private final List<Fixture> created = new ArrayList<>();

    @BeforeEach
    void login() {
        token = TestAuthClient.login(restTemplate, "admin", "admin123", "platform");
    }

    @AfterEach
    void cleanUp() {
        created.forEach(Fixture::clean);
        created.clear();
    }

    @Test
    void 首次发布把生成物推到发布分支并留下版本与发布记录() {
        Fixture fx = ready();
        String mainShaBefore = gateway.branchSha(fx.repoUrl, MAIN);

        Map<String, Object> data = dataOf(publish(fx));
        String commitSha = String.valueOf(data.get("commitSha"));
        String tagName = String.valueOf(data.get("tagName"));

        assertThat(number(data.get("status"))).isEqualTo(1);
        assertThat(number(data.get("version"))).isEqualTo(1);
        assertThat(tagName).isEqualTo("v" + fx.projectCode + "-" + PAGE_CODE + "-1");
        assertThat(data.get("branch")).isEqualTo(GEN);

        // 产物真的进了那个提交：11 份文件按交付路径落盘，工作区干净（改动全在提交里，没有漏网的）
        assertThat(castList(data.get("files"))).hasSize(FILE_COUNT);
        assertThat(workspaceFile(fx, VIEW)).contains("订单金额");
        assertThat(Files.isRegularFile(fx.workspace().resolve(EXTENSION))).isTrue();
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(commitSha);
        assertThat(gateway.isDirty(fx.workspace())).isFalse();

        // tag 是合并与回滚的锚点：它必须指到本次那个提交
        assertThat(gateway.tagSha(fx.repoUrl, tagName)).isEqualTo(versionRow(fx, 1).get("commitSha"));
        // 发布只碰发布分支，主干归人（§5.1）
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(mainShaBefore);

        Map<String, Object> version = versionRow(fx, 1);
        assertThat(version.get("commitSha")).isEqualTo(commitSha);
        // 版本的两份快照是 PR 9 回滚与「这份设计当时长什么样」的唯一凭据
        assertThat(String.valueOf(version.get("designSnapshot"))).contains("DataTable");
        assertThat(String.valueOf(version.get("filesSnapshot"))).containsPattern("[0-9a-f]{64}");

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT publish_status AS status, tag_name AS tagName, commit_sha AS commitSha"
                        + " FROM lc_publish_log WHERE page_id = ?", fx.pageId);
        assertThat(number(log.get("status"))).isEqualTo(1);
        assertThat(log.get("tagName")).isEqualTo(tagName);
        assertThat(log.get("commitSha")).isEqualTo(commitSha);

        Map<String, Object> page = jdbcTemplate.queryForMap(
                "SELECT current_version AS version, publish_status AS status FROM lc_page WHERE id = ?", fx.pageId);
        assertThat(number(page.get("version"))).isEqualTo(1);
        assertThat(number(page.get("status"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT last_commit_sha FROM lc_project WHERE id = ?", String.class, fx.projectId))
                .isEqualTo(commitSha);
        assertThat(jdbcTemplate.queryForObject("SELECT last_publish_time FROM lc_project WHERE id = ?", Object.class, fx.projectId))
                .isNotNull();

        // 设计驱动的产物也是编得过的产物：七个 java 按交付路径真起 javac
        compileGeneratedBackend(fx);
    }

    @Test
    void 重复点同一份设计发布不产生空提交也不涨版本() {
        Fixture fx = ready();
        String first = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        Map<String, Object> again = dataOf(publish(fx));
        assertThat(number(again.get("status"))).isEqualTo(2);
        assertThat(again.get("commitSha")).as("无变更时不造提交，报的就是分支上已有那个").isEqualTo(first);
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(first);

        assertThat(count("SELECT COUNT(*) FROM lc_page_version WHERE page_id = ?", fx.pageId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM lc_publish_log WHERE page_id = ?", fx.pageId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT publish_status FROM lc_publish_log ORDER BY id DESC LIMIT 1",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT current_version FROM lc_page WHERE id = ?", Integer.class, fx.pageId))
                .isEqualTo(1);
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-2")).isNull();
    }

    @Test
    void 改了设计再发布出一个新版本而旧版本记录留在历史里() {
        Fixture fx = ready();
        String first = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        // 只动「形」：把列标题从「订单金额」改成「含税金额」，模型一个字段都没碰
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);

        Map<String, Object> data = dataOf(publish(fx));
        String second = String.valueOf(data.get("commitSha"));
        assertThat(number(data.get("version"))).isEqualTo(2);
        assertThat(second).isNotEqualTo(first);
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(second);
        // 表单那一格仍叫「订单金额」，改的是列标题：只断言「整页搜不到订单金额」会把表单标签也算进去，是假失败
        assertThat(workspaceFile(fx, VIEW)).contains("title: '含税金额'").doesNotContain("title: '订单金额'");
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-2")).isEqualTo(second);
        // 两个版本各自记着自己的提交：回滚要按 tag 取上一版内容，这里断了 PR 9 就没有锚点
        assertThat(jdbcTemplate.queryForList("SELECT commit_sha FROM lc_page_version WHERE page_id = ? ORDER BY version",
                String.class, fx.pageId)).containsExactly(first, second);
    }

    @Test
    void 设计撤掉查询区再发布产物跟着少一个入口() {
        Fixture fx = ready();
        publish(fx);

        Map<String, Object> design = design("订单金额");
        design.put("layout", new LinkedHashMap<>(Map.of(
                "type", "PageContainer",
                "children", List.of(
                        tableBlock("订单金额"),
                        block("FormContainer", null, List.of(
                                control("TextInput", "订单编号", "orderNo", null),
                                control("NumberInput", "订单金额", "amount", null)))))));
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design)).getBody())
                .containsEntry("code", 200);

        assertThat(number(dataOf(publish(fx)).get("version"))).isEqualTo(2);
        // 整块查询区没了：Mapper 里不再有 LIKE 那一段（orderNo 本身还在，它仍是表单与 INSERT 的列），
        // Controller 也不再收这个查询参数
        String mapper = workspaceFile(fx, BUSINESS_DIR + "mapper/" + CLASS_NAME + "Mapper.java");
        assertThat(mapper).doesNotContain("LIKE CONCAT");
        assertThat(workspaceFile(fx, BUSINESS_DIR + "controller/" + CLASS_NAME + "Controller.java"))
                .doesNotContain("String orderNo");
        assertThat(workspaceFile(fx, VIEW)).doesNotContain("查询订单");
    }

    @Test
    void 没有工作区或页面停用都不许发布() {
        Fixture bare = new Fixture();
        // 没初始化过：工作目录都没有。发布不该替人做初始化，更不该造出一个空目录让人以为成功了
        assertThat(messageOf(publish(bare))).contains("初始化");
        assertThat(bare.workspace()).doesNotExist();

        Fixture fx = ready();
        assertThat(put("/api/lowcode/pages/" + fx.pageId + "/status", Map.of("status", 0)).getBody()).containsEntry("code", 200);
        // 停用的页面不参与发布：否则「下线」这个动作在设计器里失去意义
        assertThat(messageOf(publish(fx))).contains("停用");
    }

    @Test
    void 工作区里有未提交的改动时拒绝发布() {
        Fixture fx = ready();
        publish(fx);
        // 提交是整目录暂存：人在这份工作区里改了东西没提交，直接发布会把这份改动一起提交进发布分支
        write(fx.workspace().resolve("notes.txt"), "顺手记的两行，没打算提交\n");

        assertThat(messageOf(publish(fx))).contains("未提交").contains("丢弃");
        assertThat(count("SELECT COUNT(*) FROM lc_page_version WHERE page_id = ?", fx.pageId)).isEqualTo(1);
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-2")).isNull();
    }

    @Test
    void 发布分支被远端推进过就先拒绝_重建工作区后人工的C档内容不被骨架盖掉() {
        Fixture fx = ready();
        publish(fx);
        // 项目侧的人在发布分支上给扩展点补了一个方法（真机上是随主干合并进来的）
        gateway.humanEditAndPush(fx.repoUrl, GEN, EXTENSION,
                src -> src.replaceFirst("\\}\\s*$", "    /** 人在项目侧写的业务 */\n    public boolean isLarge() { return true; }\n}\n"));

        // 平台不强推、不自动合并：本地与远端不一致时先停下来，让人把工作区重建到与远端一致
        assertThat(messageOf(publish(fx))).contains("远端").contains("重建工作区");
        // 这份工作区没有自己的提交，重建不丢东西：这条若按「未推送提交」拒绝，人往发布分支推过一次之后项目就永远发不动
        rebuild(fx);

        // 重铺固定代码不许把人写的扩展点从工作区里删掉：那一版人补的那一行还在，就是「存在即跳过」认得出它
        assertThat(workspaceFile(fx, EXTENSION)).contains("isLarge");
        // 追上远端之后这次发布没有东西可提交：内容一致就是 no-op，不该为了「发过一次」造一个空提交
        Map<String, Object> same = dataOf(publish(fx));
        assertThat(number(same.get("status"))).isEqualTo(2);
        assertThat(number(same.get("version"))).isEqualTo(1);
        assertThat(workspaceFile(fx, EXTENSION)).contains("isLarge");

        // 有了真改动才出第二个版本，而 C 档里人写的那一行仍然不能被骨架盖掉
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        Map<String, Object> second = dataOf(publish(fx));
        assertThat(number(second.get("version"))).isEqualTo(2);
        assertThat(skipped(second, "EXTENSION")).isEqualTo(1);
        assertThat(workspaceFile(fx, EXTENSION)).contains("isLarge");
    }

    @Test
    void 推送失败时不留下成功版本_重试把同一个提交推上去() {
        Fixture fx = ready();
        gateway.refuseNextPush();

        assertThat(messageOf(publish(fx))).contains("推送");
        // 远端仍停在固定代码那个提交，也没多出任何 tag
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(fx.snapshotSha);
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-1")).isNull();
        assertThat(count("SELECT COUNT(*) FROM lc_page_version WHERE page_id = ?", fx.pageId)).isZero();

        Map<String, Object> failed = jdbcTemplate.queryForMap(
                "SELECT publish_status AS status, commit_sha AS commitSha FROM lc_publish_log WHERE page_id = ?", fx.pageId);
        assertThat(number(failed.get("status"))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT publish_status FROM lc_page WHERE id = ?", Integer.class, fx.pageId))
                .isEqualTo(3);

        // 本地那个提交是真实存在的：重试必须把它推上去，而不是再叠一个内容相同的提交
        String unpushed = String.valueOf(failed.get("commitSha"));
        Map<String, Object> data = dataOf(publish(fx));
        assertThat(data.get("commitSha")).isEqualTo(unpushed);
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(unpushed);
        assertThat(number(data.get("version"))).isEqualTo(1);
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-1")).isEqualTo(unpushed);
    }

    @Test
    void 扩展点还在引用被删掉的字段时发布被拦而带force才放行() {
        Fixture fx = ready();
        publish(fx);
        // 人在扩展点里补了一段用 deliveryDate 的业务，推上发布分支后让平台的工作区追上远端
        gateway.humanEditAndPush(fx.repoUrl, GEN, EXTENSION, src -> src.replace(
                "// TODO 人工填写：平台只在首次发布生成本类，之后不再覆盖",
                "rows.forEach(row -> row.setOrderNo(row.getDeliveryDate() + \"|\" + row.getOrderNo()));"));
        rebuild(fx);

        // 这一列先从设计里撤掉（不然保存就先拦），再从模型里删掉：页面存得下来，扩展点却还认它
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, designWithoutDate())).getBody())
                .containsEntry("code", 200);
        assertThat(delete("/api/lowcode/fields/" + fx.fieldIds.get("deliveryDate")).getBody()).containsEntry("code", 200);

        // 默认阻断：等交付项目编译失败时人已经在合并请求里了，而 diff 只会显示「生成物变了」
        assertThat(messageOf(publish(fx))).contains("deliveryDate").contains("扩展点").contains("force");
        assertThat(count("SELECT COUNT(*) FROM lc_publish_log WHERE page_id = ?", fx.pageId)).isEqualTo(1);

        // 显式放行才发得出去，而那些命中要原样留在结果里：发出去了，但人得知道自己刚才放过了什么
        Map<String, Object> forced = dataOf(publish(fx, true));
        assertThat(number(forced.get("status"))).isEqualTo(1);
        assertThat(number(forced.get("version"))).isEqualTo(2);
        assertThat(castList(forced.get("warnings"))).isNotEmpty();
    }

    /* ── 现场 ── */

    /**
     * 版本历史是回滚的取数入口（PR 9）：人得先看清「有哪几版、各是哪一笔提交、里面有哪些文件」，
     * 才谈得上「退回其中一版」。库里那两张 JSON 列一直在写，之前没有任何读路径 —— 只有写没有读的表
     * 等于一张收据没人翻。
     */
    @Test
    void 版本历史按版本倒序返回且每一版带着自己的tag与提交号() {
        Fixture fx = ready();
        String first = String.valueOf(dataOf(publish(fx)).get("commitSha"));
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        String second = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        Map<String, Object> data = dataOf(exchange(HttpMethod.GET,
                "/api/lowcode/pages/" + fx.pageId + "/versions?pageNum=1&pageSize=10", null));
        assertThat(data).containsEntry("total", 2);
        List<Map<String, Object>> rows = new ArrayList<>();
        castList(data.get("rows")).forEach(item -> rows.add(castMap(item)));
        // 倒序：最新那一版在最上面，「回滚到上一版」在界面上就是第二行那个按钮
        assertThat(rows).extracting(row -> number(row.get("version"))).containsExactly(2L, 1L);
        assertThat(rows.get(0)).containsEntry("commitSha", second)
                .containsEntry("tagName", "v" + fx.projectCode + "-" + PAGE_CODE + "-2")
                .containsEntry("publishType", "UPDATE");
        assertThat(rows.get(1)).containsEntry("commitSha", first).containsEntry("publishType", "CREATE");
        // 文件数在 SQL 里按 JSON_LENGTH 现算：不存计数列（V9 挡掉三个计数的同一口径），也不回传整份快照
        assertThat(number(rows.get(0).get("fileCount"))).isEqualTo(FILE_COUNT);
        assertThat(rows.get(0)).doesNotContainKey("filesSnapshot");
    }

    @Test
    void 单版详情给出文件清单并标出哪几档不参与回滚() {
        Fixture fx = ready();
        String first = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        Map<String, Object> data = dataOf(exchange(HttpMethod.GET,
                "/api/lowcode/pages/" + fx.pageId + "/versions/1", null));
        assertThat(data).containsEntry("version", 1).containsEntry("commitSha", first);
        List<Map<String, Object>> rows = new ArrayList<>();
        castList(data.get("files")).forEach(item -> rows.add(castMap(item)));
        assertThat(rows).hasSize(FILE_COUNT);
        // 档位由 GeneratedPaths.tier 现场反推，而不是信快照：路径是唯一事实源，
        // 快照里根本没有 tier 这一维（V9 那份只存 路径 → 哈希）
        assertThat(rows).anySatisfy(row -> {
            assertThat(row).containsEntry("path", EXTENSION).containsEntry("tier", "EXTENSION")
                    .containsEntry("rollbackable", false);
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row).containsEntry("path", VIEW).containsEntry("tier", "GENERATED")
                    .containsEntry("rollbackable", true);
        });
        // 哈希是回滚前后要对账的那个号，缺了它就只能「信仓库」
        assertThat(String.valueOf(rows.get(0).get("sha256"))).hasSize(64);
    }

    /**
     * 回滚到上一版：发布分支上多一笔新提交，内容回到那一版，而主干一个提交都没多。
     *
     * <p>这条是 §5.4 那句「一律以新提交实现，不倒带分支历史」的正面验收：历史是 append-only 的，
     * v3 的内容等于 v1，但 v2 仍在历史里、仍能被指出来、仍能被回滚过去。
     */
    @Test
    void 回滚到上一版在发布分支上加一笔新提交而主干不动() {
        Fixture fx = ready();
        String v1 = String.valueOf(dataOf(publish(fx)).get("commitSha"));
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        String v2 = String.valueOf(dataOf(publish(fx)).get("commitSha"));
        assertThat(workspaceFile(fx, VIEW)).contains("title: '含税金额'");
        String mainBefore = gateway.branchSha(fx.repoUrl, MAIN);

        Map<String, Object> data = dataOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 1)));
        assertThat(data).containsEntry("version", 3).containsEntry("rolledBackTo", 1)
                .containsEntry("publishType", "ROLLBACK")
                .containsEntry("tagName", "v" + fx.projectCode + "-" + PAGE_CODE + "-3");
        String v3 = String.valueOf(data.get("commitSha"));
        // 内容真的回到 v1：断言的是文件里那一行，不是「接口说成功了」
        assertThat(workspaceFile(fx, VIEW)).contains("title: '订单金额'").doesNotContain("title: '含税金额'");

        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(v3);
        assertThat(gateway.tagSha(fx.repoUrl, "v" + fx.projectCode + "-" + PAGE_CODE + "-3")).isEqualTo(v3);
        // 主干没被碰：回滚只写发布分支，要进主干仍然得走合并请求、由人确认
        assertThat(gateway.branchSha(fx.repoUrl, MAIN)).isEqualTo(mainBefore);
        // 历史没被倒带：三笔提交各指向自己那一版，v2 仍然在，也仍然能被回滚过去
        assertThat(jdbcTemplate.queryForList("SELECT commit_sha FROM lc_page_version WHERE page_id = ? ORDER BY version",
                String.class, fx.pageId)).containsExactly(v1, v2, v3);
        assertThat(jdbcTemplate.queryForList("SELECT publish_type FROM lc_publish_log"
                        + " WHERE page_id = ? AND publish_status = 1 ORDER BY version", String.class, fx.pageId))
                .containsExactly("CREATE", "UPDATE", "ROLLBACK");
        assertThat(count("SELECT COUNT(*) FROM lc_page WHERE id = ?", fx.pageId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT current_version FROM lc_page WHERE id = ?", Integer.class,
                fx.pageId)).isEqualTo(3);
    }

    /**
     * 文件级回滚：只把选中的那几个文件还原，其余保持当前这一版。
     *
     * <p>用例先证明「这两个文件在 v1 与 v2 之间确实不同」，否则「只回滚了一个」这句断言是空的 ——
     * 两个文件本来就一样的话，怎么回滚都看不出区别。
     */
    @Test
    void 文件级回滚只还原选中的那几个文件() {
        Fixture fx = ready();
        publish(fx);                                    // v1：表单里有「交货日期」
        String viewV1 = workspaceFile(fx, VIEW);
        String requestV1 = workspaceFile(fx, WRITE_REQUEST);

        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, designWithoutDate())).getBody())
                .containsEntry("code", 200);
        publish(fx);                                    // v2：把那一格从表单里撤走
        // 先证明这两个文件在两版之间都变了 —— 不然「只回滚了一个」这句断言是空的
        assertThat(workspaceFile(fx, VIEW)).isNotEqualTo(viewV1);
        assertThat(workspaceFile(fx, WRITE_REQUEST)).isNotEqualTo(requestV1);

        Map<String, Object> data = dataOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback",
                Map.of("version", 1, "files", List.of(VIEW))));
        assertThat(files(data)).extracting(row -> row.get("path")).containsExactly(VIEW);
        assertThat(workspaceFile(fx, VIEW)).isEqualTo(viewV1);
        // 没被点名的文件一个字都没动：文件级的意义就在这
        assertThat(workspaceFile(fx, WRITE_REQUEST)).isNotEqualTo(requestV1);
    }

    @Test
    void C档扩展点不参与回滚因为平台不生成它也没权还原它() {
        Fixture fx = ready();
        publish(fx);
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        publish(fx);

        assertThat(messageOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback",
                Map.of("version", 1, "files", List.of(EXTENSION)))))
                .contains("扩展点").contains(EXTENSION);
        // 整页回滚的清单里也没有它：判据是路径反推出的档位，不是调用方传没传
        Map<String, Object> data = dataOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 1)));
        assertThat(files(data)).allSatisfy(row -> assertThat(row.get("path")).isNotEqualTo(EXTENSION));
    }

    @Test
    void 回滚到当前这一版本被拒因为那等于什么都不做() {
        Fixture fx = ready();
        publish(fx);
        assertThat(messageOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 1))))
                .contains("当前的那一版").contains("什么都不做");
    }

    @Test
    void 没有的那一版不许回滚也不留下提交() {
        Fixture fx = ready();
        String v1 = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        assertThat(messageOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 7))))
                .contains("v7");
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(v1);
        assertThat(count("SELECT COUNT(*) FROM lc_page_version WHERE page_id = ?", fx.pageId)).isEqualTo(1);
    }

    /**
     * 工作区脏着不许回滚 —— 与发布同一条守卫，因为两者都是「整目录暂存 + 提交」。
     *
     * <p>这条同时证明两个动作共用同一份守卫代码：改一个而漏另一个，就会出现「发布拦得住、回滚拦不住」。
     */
    @Test
    void 工作区里有未提交的改动时拒绝回滚() {
        Fixture fx = ready();
        publish(fx);
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        publish(fx);

        write(fx.workspace().resolve("人工草稿.txt"), "有人在仓库里写了没提交\n");
        assertThat(messageOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 1))))
                .contains("未提交的改动");
        // 不用手工清掉那个草稿文件：夹具的 clean() 连工作区目录一起删，
        // 而这里要验的就是「脏着的时候不许写」，不是「写完能不能收拾干净」
    }

    /**
     * 负向对照：库里的账与仓库内容对不上时必须停手。
     *
     * <p>做法是把 v1 那一版的快照哈希改坏一位。真机上这种状态来自「快照写成功但产物后来被人在发布分支上改了」
     * 或版本行写错 —— 两种都不该由回滚默默吞掉：吞掉就意味着把一份没人核对过的内容推上发布分支。
     */
    @Test
    void 快照哈希与仓库内容对不上时拒绝回滚() {
        Fixture fx = ready();
        publish(fx);
        assertThat(put("/api/lowcode/pages/" + fx.pageId, pageBody(fx.projectId, design("含税金额"))).getBody())
                .containsEntry("code", 200);
        String v2 = String.valueOf(dataOf(publish(fx)).get("commitSha"));

        int changed = jdbcTemplate.update("UPDATE lc_page_version SET files_snapshot ="
                + " JSON_SET(files_snapshot, '$.\"scaffold-vue/src/views/sales_order/SalesOrder.vue\"',"
                + " REPEAT('0', 64)) WHERE page_id = ? AND version = 1", fx.pageId);
        assertThat(changed).isEqualTo(1);

        assertThat(messageOf(post("/api/lowcode/pages/" + fx.pageId + "/rollback", Map.of("version", 1))))
                .contains("对不上");
        assertThat(gateway.branchSha(fx.repoUrl, GEN)).isEqualTo(v2);
        assertThat(count("SELECT COUNT(*) FROM lc_page_version WHERE page_id = ?", fx.pageId)).isEqualTo(2);
    }

    /**
     * 一个可发布的现场：项目 + 远端主干 + 模型字段 + 页面设计。
     *
     * <p>主干是预先在远端建好的：项目仓库在真实世界里本来就有主干，初始化走的「clone 主干 → 从主干切发布分支」
     * 那条分支，重建工作区时才认得出这是脚手架仓库（§6.3）。
     */
    private final class Fixture {
        private final String projectCode;
        private final String repoUrl;
        private final long projectId;
        private final long modelId;
        private final long pageId;
        private final Map<String, Long> fieldIds = new LinkedHashMap<>();
        /** 初始化推到发布分支上那个提交：发布失败用例拿它当「远端仍停在原地」的基准 */
        private String snapshotSha;

        private Fixture() {
            created.add(this);
            this.projectCode = "pub" + Math.abs(System.nanoTime() % 100_000_000);
            this.repoUrl = "git@github.com:acme/" + projectCode + ".git";
            gateway.seedMainBranch(repoUrl, projectCode);

            Map<String, Object> project = new LinkedHashMap<>();
            project.put("projectCode", projectCode);
            project.put("projectName", "发布链路验收");
            project.put("repoUrl", repoUrl);
            project.put("mainBranch", MAIN);
            project.put("genBranch", GEN);
            this.projectId = number(dataOf(post("/api/lowcode/projects", project)).get("id"));

            Map<String, Object> model = new LinkedHashMap<>();
            model.put("projectId", projectId);
            model.put("modelCode", MODEL_CODE);
            model.put("modelName", "销售订单");
            model.put("modelDesc", "销售订单主表");
            this.modelId = number(dataOf(post("/api/lowcode/models", model)).get("id"));
            addField("id", "主键", "INTEGER", Map.of("isPrimary", 1, "isAutoIncrement", 1, "isNullable", 0, "isList", 1));
            addField("orderNo", "订单编号", "STRING", Map.of("columnLength", 64, "isRequired", 1, "isUnique", 1));
            addField("amount", "订单金额", "DECIMAL", Map.of("columnPrecision", 18, "columnScale", 2));
            addField("status", "状态", "INTEGER", Map.of("dictType", "sys_normal_disable"));
            addField("deliveryDate", "交货日期", "DATE", Map.of());
            addField("remark", "备注", "STRING", Map.of("columnLength", 255, "formType", "TEXTAREA"));
            this.pageId = number(dataOf(post("/api/lowcode/pages", pageBody(projectId, design("订单金额")))).get("id"));
        }

        private void addField(String code, String name, String type, Map<String, Object> extras) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fieldCode", code);
            body.put("fieldName", name);
            body.put("fieldType", type);
            body.putAll(extras);
            fieldIds.put(code, number(dataOf(post("/api/lowcode/models/" + modelId + "/fields", body)).get("id")));
        }

        private Path workspace() {
            return Path.of(System.getProperty("java.io.tmpdir"), "scaffold-it-publish", "1", projectCode);
        }

        private void clean() {
            jdbcTemplate.update("DELETE FROM lc_publish_log WHERE page_id IN (SELECT id FROM lc_page WHERE project_id = ?)", projectId);
            jdbcTemplate.update("DELETE FROM lc_page_version WHERE page_id IN (SELECT id FROM lc_page WHERE project_id = ?)", projectId);
            jdbcTemplate.update("DELETE FROM lc_page WHERE project_id = ?", projectId);
            jdbcTemplate.update("DELETE FROM lc_field WHERE model_id = ?", modelId);
            jdbcTemplate.update("DELETE FROM lc_model WHERE id = ?", modelId);
            jdbcTemplate.update("DELETE FROM lc_project WHERE id = ?", projectId);
            deleteRecursively(workspace());
            deleteRecursively(gateway.remoteDirOf(repoUrl));
        }
    }

    private Fixture ready() {
        Fixture fx = new Fixture();
        ResponseEntity<Map<String, Object>> initialize = post("/api/lowcode/projects/" + fx.projectId + "/initialize", Map.of());
        assertThat(initialize.getBody()).as(String.valueOf(initialize.getBody())).containsEntry("code", 200);
        fx.snapshotSha = gateway.branchSha(fx.repoUrl, GEN);
        return fx;
    }

    private ResponseEntity<Map<String, Object>> publish(Fixture fx) {
        return publish(fx, false);
    }

    private ResponseEntity<Map<String, Object>> publish(Fixture fx, boolean force) {
        return post("/api/lowcode/pages/" + fx.pageId + "/publish", Map.of("force", force));
    }

    /** 平台的工作区是发布分支的缓存：追上远端就走「清空 + 重新初始化」，人写的东西只有这样才能被认出来 */
    private void rebuild(Fixture fx) {
        assertThat(delete("/api/lowcode/projects/" + fx.projectId + "/workspace").getBody()).containsEntry("code", 200);
        assertThat(post("/api/lowcode/projects/" + fx.projectId + "/initialize", Map.of()).getBody()).containsEntry("code", 200);
    }

    /* ── 设计 ── */

    /** 一份能生成出来的列表页：查询一条、工具栏一个新增、两列、表单四格。金额列标题由用例改，用来造第二次发布 */
    private static Map<String, Object> design(String amountTitle) {
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("version", "1.0");
        design.put("pageType", "list");
        design.put("modelRef", MODEL_CODE);
        design.put("layout", new LinkedHashMap<>(Map.of(
                "type", "PageContainer",
                "props", new LinkedHashMap<>(Map.of("title", "销售订单", "desc", "管理所有销售订单")),
                "children", List.of(
                        block("SearchForm", Map.of("columns", 3), List.of(
                                control("TextInput", "查询订单", "orderNo", "LIKE"))),
                        block("Toolbar", null, List.of(Map.of("type", "Button",
                                "props", new LinkedHashMap<>(Map.of("label", "新增", "action", "create"))))),
                        tableBlock(amountTitle),
                        block("FormContainer", null, List.of(
                                control("TextInput", "订单编号", "orderNo", null),
                                control("NumberInput", "订单金额", "amount", null),
                                control("DateInput", "交货日期", "deliveryDate", null),
                                control("Textarea", "备注", "remark", null)))))));
        return design;
    }

    /** 同一份设计，只是把「交货日期」那一格从表单里撤走：撤不掉就存不进库，也就轮到不了引用体检 */
    private static Map<String, Object> designWithoutDate() {
        Map<String, Object> design = design("订单金额");
        design.put("layout", new LinkedHashMap<>(Map.of(
                "type", "PageContainer",
                "props", new LinkedHashMap<>(Map.of("title", "销售订单", "desc", "管理所有销售订单")),
                "children", List.of(
                        block("SearchForm", Map.of("columns", 3), List.of(
                                control("TextInput", "查询订单", "orderNo", "LIKE"))),
                        block("Toolbar", null, List.of(Map.of("type", "Button",
                                "props", new LinkedHashMap<>(Map.of("label", "新增", "action", "create"))))),
                        tableBlock("订单金额"),
                        block("FormContainer", null, List.of(
                                control("TextInput", "订单编号", "orderNo", null),
                                control("NumberInput", "订单金额", "amount", null),
                                control("Textarea", "备注", "remark", null)))))));
        return design;
    }

    private static Map<String, Object> tableBlock(String amountTitle) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "DataTable");
        table.put("columns", List.of(
                new LinkedHashMap<>(Map.of("field", "orderNo", "title", "订单编号")),
                new LinkedHashMap<>(Map.of("field", "amount", "title", amountTitle, "align", "right"))));
        return table;
    }

    private static Map<String, Object> block(String type, Map<String, Object> props, List<Map<String, Object>> children) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        if (props != null) {
            block.put("props", new LinkedHashMap<>(props));
        }
        block.put("children", new ArrayList<>(children));
        return block;
    }

    private static Map<String, Object> control(String type, String label, String field, String queryType) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("label", label);
        if (field != null) {
            props.put("field", field);
        }
        if (queryType != null) {
            props.put("queryType", queryType);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("props", props);
        return node;
    }

    private Map<String, Object> pageBody(long projectId, Map<String, Object> design) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("modelCode", MODEL_CODE);
        body.put("pageCode", PAGE_CODE);
        body.put("pageName", "销售订单列表");
        body.put("design", design);
        return body;
    }

    /* ── 产物核对 ── */

    private Map<String, Object> versionRow(Fixture fx, int version) {
        return jdbcTemplate.queryForMap("SELECT version, commit_sha AS commitSha, design_snapshot AS designSnapshot,"
                + " files_snapshot AS filesSnapshot FROM lc_page_version WHERE page_id = ? AND version = ?",
                fx.pageId, version);
    }

    private int count(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private List<Map<String, Object>> files(Map<String, Object> data) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (Object item : castList(data.get("files"))) {
            Map<String, Object> row = castMap(item);
            files.add(row);
        }
        return files;
    }

    /** 发布结果里的落盘动作：SKIPPED 的条数就是「保住人写内容」的那几个文件 */
    private long skipped(Map<String, Object> data, String tier) {
        return files(data).stream()
                .filter(row -> tier.equals(row.get("tier")) && "SKIPPED".equals(row.get("action")))
                .count();
    }

    /** 生成物按交付路径真起 javac：模块没被聚合、包名对不上目录这类错，只有编译器会认 */
    private void compileGeneratedBackend(Fixture fx) {
        Path modelDir = fx.workspace().resolve(BUSINESS_DIR);
        List<Path> sources = new ArrayList<>();
        try (var walk = Files.walk(modelDir)) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        } catch (Exception e) {
            throw new AssertionError("遍历生成物失败：" + e.getMessage(), e);
        }
        assertThat(sources).as("这个模型目录下应有七个 java 产物").hasSize(7);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("这条用例需要 JDK 自带的 javac").isNotNull();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path classes;
        try {
            classes = Files.createDirectories(tempDir.resolve("classes"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        List<String> command = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-d", classes.toString(), "-classpath", System.getProperty("java.class.path")));
        sources.forEach(path -> command.add(path.toString()));
        int code = compiler.run(null, null, err, command.toArray(String[]::new));
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(code).as("javac 退出码").isZero();
    }

    private String workspaceFile(Fixture fx, String relativePath) {
        try {
            return Files.readString(fx.workspace().resolve(relativePath), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("工作区里读不到 " + relativePath + "：" + e.getMessage(), e);
        }
    }

    private void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("写不进 " + file + "：" + e.getMessage(), e);
        }
    }

    private static void deleteRecursively(Path dir) {
        LocalBareGitGateway.deleteRecursively(dir);
    }

    /* ── HTTP ── */

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, body);
    }

    private ResponseEntity<Map<String, Object>> put(String path, Map<String, Object> body) {
        return exchange(HttpMethod.PUT, path, body);
    }

    private ResponseEntity<Map<String, Object>> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null);
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    /** 业务异常统一按 400 返回：失败分支只取提示语 */
    private String messageOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return String.valueOf(response.getBody().get("message"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Map<String, Object> body) {
        assertThat(body).containsEntry("code", 200);
        return (Map<String, Object>) body.get("data");
    }

    /** 成功分支直接吃响应实体：失败分支才要 messageOf */
    private Map<String, Object> dataOf(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).isNotNull();
        return dataOf(response.getBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    private static long number(Object value) {
        return value instanceof Number num ? num.longValue() : Long.parseLong(String.valueOf(value));
    }
}
