package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 裁剪清单（方案 §6.4）与标记块删除。这里的断言不是走形式：交付产物里没有低代码平台自身，
 * 是「发布产物的定义」，清单一改就会红。
 */
class ScaffoldSnapshotPlannerTest {

    /** 真实底座 checkout：从测试工作目录（scaffold-system/scaffold-lowcode）向上找，走的是生产同一条定位逻辑 */
    private static final Path SOURCE_ROOT = ScaffoldSourceLocator.locate("");

    /** 走一次真实底座的裁剪，后面所有断言共用这份计划。版本写成显眼的哨兵值，交付改写是否生效一眼可见 */
    private static final String TEST_UI_VERSION = "^9.9.9-delivery-test";
    /** 演练用的项目名：交付品牌要按它改写，登录页与侧栏不许再出现平台自己的名字 */
    private static final String TEST_PROJECT_NAME = "订单中台";
    private static final ScaffoldSnapshot SNAPSHOT = ScaffoldSnapshotPlanner.plan(SOURCE_ROOT, TEST_UI_VERSION, TEST_PROJECT_NAME);

    @Test
    void 交付项目按创建它的项目名展示品牌() {
        // 交付的是这个项目自己的后台，不是平台的一个副本：登录页大字、侧栏品牌、浏览器标签
        // 全都读 src/data/index.ts 那一份 APP —— 平台的名字留在这里，客户项目就永远是「元搭低代码平台」
        String brand = textOf("scaffold-vue/src/data/index.ts");
        assertThat(brand).contains("name: '订单中台'").contains("shortName: '订单中台'");
        assertThat(textOf("scaffold-vue/src/views/Login.vue")).doesNotContain("元搭");
    }

    @Test
    void 交付里不出现平台品牌也不吹自己没有的功能() {
        // 品牌关键字一旦漏进交付产物，守卫要当场拦下（与平台代码引用同一套做法）：
        // 这些字符串在页面上、注释里，客户每天看得见
        for (SnapshotFile file : SNAPSHOT.files()) {
            String lower = file.path().toLowerCase(java.util.Locale.ROOT);
            if (!(lower.endsWith(".java") || lower.endsWith(".ts") || lower.endsWith(".vue") || lower.endsWith(".sql"))) {
                continue;
            }
            String content = new String(ScaffoldSnapshotPlanner.content(file), StandardCharsets.UTF_8);
            assertThat(content).as("交付文件 " + file.path() + " 不该带平台品牌").doesNotContain("元搭");
        }
        // 登录页那六条能力里，「代码生成」指的是被裁掉的 scaffold-generator 页面：交付项目里没有它
        assertThat(textOf("scaffold-vue/src/views/Login.vue")).doesNotContain("title: '代码生成'");
    }

    @Test
    void 平台自身的模块页面与接口不进交付项目() {
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-lowcode/pom.xml")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-generator/pom.xml")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/views/lowcode/models.vue")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/views/tool/Generator.vue")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/api/platform.ts")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/sql/V6__lowcode_model.sql")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/sql/V7__lowcode_workspace.sql")).isFalse();
        // 构建产物与依赖目录在任何层级都不进快照
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/node_modules/vue/index.js")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-common/target/classes/a.class")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/.DS_Store")).isFalse();
        // 平台本地工作区里是各项目 clone 出去的交付物：不排除就会把上一版交付整个套进新一版
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-admin/lowcode-workspace/1/demo/README.md")).isFalse();
        // 平台侧说明文档与仓库自身的变更日志不带
        assertThat(ScaffoldTrimPolicy.accepts("docs/lowcode-platform-proposal.md")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("PROJECT_MAP.md")).isFalse();
        assertThat(ScaffoldTrimPolicy.accepts("changelog.md")).isFalse();
    }

    @Test
    void 底座能力必须整体进交付项目() {
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-business/pom.xml")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/scaffold-admin/src/main/resources/application.yml")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/views/Login.vue")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/sql/V1__init_schema.sql")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("docs/docker-deployment.md")).isTrue();
        // app 壳是 A 档固定代码：外壳组件与登录页神经网络随项目走
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/components/layout/AppShell.vue")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/components/NeuralNet.vue")).isTrue();
        // 只服务被剔除页面的流水线组件留在宿主的 platform 目录，也不进交付项目
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/components/platform/GeneratorPipeline.vue")).isFalse();
        // 交付项目里既没有旧的 components/business 目录，也没有物料包源码 —— 组件走 npm 依赖
        assertThat(SNAPSHOT.paths()).noneMatch(path -> path.startsWith("scaffold-vue/src/components/business/"));
        // 物料包 scope 固定走官方源：镜像同步新包有延迟，少了这个文件交付项目会 ETARGET 装不上
        assertThat(SNAPSHOT.paths()).contains("scaffold-vue/.npmrc");
    }

    @Test
    void 交付项目按npm依赖拿物料包而不是拷源码() {
        // 包与平台侧 workspace 清单、根忽略规则都不进交付项目
        assertThat(SNAPSHOT.paths()).noneMatch(path -> path.startsWith("packages/"));
        assertThat(SNAPSHOT.paths())
                .noneMatch(path -> path.equals("package.json"))
                .noneMatch(path -> path.equals("package-lock.json"))
                .noneMatch(path -> path.equals(".dockerignore"));
        assertThat(ScaffoldTrimPolicy.accepts("packages/scaffold-ui/src/components/business/ListPage.vue")).isFalse();

        // 依赖行被改写成配置里的版本，底座那条 workspace 通配符不能漏出去
        String frontendPom = textOf("scaffold-vue/package.json");
        assertThat(frontendPom).contains("\"@yuanda-lowcode-ai/scaffold-ui\": \"" + TEST_UI_VERSION + "\"")
                .doesNotContain("\"@yuanda-lowcode-ai/scaffold-ui\": \"*\"");

        // 前端镜像与 compose 换回「上下文 = scaffold-vue」的交付写法
        String dockerfile = textOf("scaffold-vue/Dockerfile");
        assertThat(dockerfile).contains("COPY package.json ./")
                .contains("RUN npm install")
                .contains("RUN npm run build")
                .contains("COPY nginx.conf /etc/nginx/conf.d/default.conf")
                .doesNotContain("npm --workspace")
                .doesNotContain("packages/scaffold-ui");
        String compose = textOf("docker-compose.yml");
        assertThat(compose).contains("context: ./scaffold-vue")
                .doesNotContain("dockerfile: scaffold-vue/Dockerfile");

        // 说明与部署文档也换成交付视角，别让项目里读到一个不存在的仓库根 workspace
        assertThat(textOf("README.md")).contains("python3 scripts/session-regression.py")
                .doesNotContain("packages/scaffold-ui");
        assertThat(textOf("docs/docker-deployment.md")).contains("组件与珍珠玻璃主题来自 npm 依赖 `@yuanda-lowcode-ai/scaffold-ui`")
                .contains("前端命令在 `scaffold-vue` 目录跑")
                .doesNotContain("npm workspaces");
    }

    @Test
    void 交付改写块被改动时当场失败() throws Exception {
        // 只建裁剪清单要求的那些根路径，再把 package.json 的依赖行改歪：改写块找不到就必须拒绝，
        // 而不是推一个装不上依赖的项目到租户仓库
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("scaffold-delivery-rewrite");
        try {
            for (String required : ScaffoldTrimPolicy.requiredRoots()) {
                java.nio.file.Path file = root.resolve(required);
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, required.endsWith("package.json")
                        ? "{\n  \"dependencies\": {\n    \"@yuanda-lowcode-ai/scaffold-ui\": \"file:../../whatever\"\n  }\n}\n"
                        : "");
            }
            assertThatThrownBy(() -> ScaffoldSnapshotPlanner.plan(root, "^1.2.3", "订单中台"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("交付改写失败")
                    .hasMessageContaining("scaffold-vue/package.json");
        } finally {
            deleteTree(root);
        }
    }

    private static void deleteTree(java.nio.file.Path root) throws java.io.IOException {
        try (var walk = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void 对真实底座出快照() {
        assertThat(SNAPSHOT.files()).isNotEmpty();
        assertThat(SNAPSHOT.paths())
                .anyMatch(path -> path.equals("scaffold-system/scaffold-business/pom.xml"))
                .anyMatch(path -> path.equals("scaffold-vue/src/views/Login.vue"))
                .anyMatch(path -> path.equals("scaffold-system/sql/V1__init_schema.sql"));
        // 平台的东西一个都不该在
        assertThat(SNAPSHOT.paths())
                .noneMatch(path -> path.startsWith("scaffold-system/scaffold-lowcode/"))
                .noneMatch(path -> path.startsWith("scaffold-system/scaffold-generator/"))
                .noneMatch(path -> path.startsWith("scaffold-vue/src/views/lowcode/"))
                .noneMatch(path -> path.startsWith("scaffold-vue/src/views/tool/"))
                .noneMatch(path -> path.contains("lowcode-workspace/"));
    }

    @Test
    void 交付的父pom与启动类不再装配平台模块() {
        String parentPom = textOf("scaffold-system/pom.xml");
        assertThat(parentPom).doesNotContain("<module>scaffold-lowcode</module>")
                .doesNotContain("<module>scaffold-generator</module>")
                .doesNotContain("<artifactId>scaffold-lowcode</artifactId>")
                .contains("<module>scaffold-business</module>");

        String adminPom = textOf("scaffold-system/scaffold-admin/pom.xml");
        assertThat(adminPom).doesNotContain("<artifactId>scaffold-lowcode</artifactId>")
                .doesNotContain("<artifactId>scaffold-generator</artifactId>")
                .contains("<artifactId>scaffold-business</artifactId>");

        String application = textOf("scaffold-system/scaffold-admin/src/main/java/com/scaffold/admin/ScaffoldAdminApplication.java");
        assertThat(application).doesNotContain("com.scaffold.lowcode")
                .doesNotContain("com.scaffold.generator.mapper")
                .contains("\"com.scaffold.system.mapper\",")
                .contains("\"com.scaffold.business\"");
    }

    @Test
    void 交付的建表脚本里没有平台元数据表() {
        String schema = textOf("scaffold-system/sql/V1__init_schema.sql");
        assertThat(schema).doesNotContain("gen_table")
                .contains("CREATE TABLE IF NOT EXISTS lc_tenant")
                .contains("CREATE TABLE IF NOT EXISTS sys_user");

        String seed = textOf("scaffold-system/sql/V2__seed_data.sql");
        assertThat(seed).doesNotContain("'tool:gen:list'")
                .doesNotContain("/tool/generator")
                // 租户注册表种子行必须留着：登录要查租户是否停用
                .contains("INSERT IGNORE INTO lc_tenant")
                // 基础菜单不能一起被删掉
                .contains("'用户管理'");
    }

    @Test
    void 交付的前端入口不再引用平台接口() {
        String barrel = textOf("scaffold-vue/src/api/index.ts");
        assertThat(barrel).doesNotContain("platform.ts")
                .doesNotContain("platformApi")
                .contains("systemApi");
    }

    @Test
    void 标记块不成对时当场失败而不是产出半成品() {
        assertThatThrownBy(() -> PlatformOnlyStripper.stripLines(List.of(
                "a",
                "// scaffold:platform-only:start",
                "b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 end");

        assertThatThrownBy(() -> PlatformOnlyStripper.stripLines(List.of(
                "a",
                "// scaffold:platform-only:end")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有对应的 start");

        // 标记必须独占整行：同行成对出现是数不平的，直接报错而不是产出半截文件
        assertThatThrownBy(() -> PlatformOnlyStripper.stripLines(List.of(
                "text <!-- scaffold:platform-only:start -->x<!-- scaffold:platform-only:end --> more")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("独占整行");

        assertThat(PlatformOnlyStripper.stripLines(List.of("a", "b"))).containsExactly("a", "b");
    }

    @Test
    void 底座目录不存在时给出可操作的提示() {
        assertThatThrownBy(() -> ScaffoldSnapshotPlanner.plan(Path.of("/definitely/not/here"), "^1.0.0", "订单中台"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scaffold.lowcode.scaffold-source-root");
    }

    private static String textOf(String path) {
        SnapshotFile file = SNAPSHOT.find(path);
        assertThat(file).as(path + " 必须在交付清单里").isNotNull();
        return new String(ScaffoldSnapshotPlanner.content(file), StandardCharsets.UTF_8);
    }
}
