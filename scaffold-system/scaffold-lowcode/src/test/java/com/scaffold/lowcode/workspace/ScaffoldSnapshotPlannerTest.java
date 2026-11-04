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

    /** 走一次真实底座的裁剪，后面所有断言共用这份计划 */
    private static final ScaffoldSnapshot SNAPSHOT = ScaffoldSnapshotPlanner.plan(SOURCE_ROOT);

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
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/components/business/ListPage.vue")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-system/sql/V1__init_schema.sql")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("docs/docker-deployment.md")).isTrue();
        // 其余业务组件与系统页面一起保留：部门图谱、权限矩阵这些是系统页在用的
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/components/business/OrgCanvas.vue")).isTrue();
        assertThat(ScaffoldTrimPolicy.accepts("scaffold-vue/src/views/system/Roles.vue")).isTrue();
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
                .noneMatch(path -> path.startsWith("scaffold-vue/src/views/tool/"));
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
        assertThatThrownBy(() -> ScaffoldSnapshotPlanner.plan(Path.of("/definitely/not/here")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scaffold.lowcode.scaffold-source-root");
    }

    private static String textOf(String path) {
        SnapshotFile file = SNAPSHOT.find(path);
        assertThat(file).as(path + " 必须在交付清单里").isNotNull();
        return new String(ScaffoldSnapshotPlanner.content(file), StandardCharsets.UTF_8);
    }
}
