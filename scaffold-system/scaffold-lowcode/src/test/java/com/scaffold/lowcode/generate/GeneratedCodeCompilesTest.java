package com.scaffold.lowcode.generate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.project;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 产物真编译（PR 5B 的验收项）。
 *
 * <p>形状断言抓不到编译器才抓得到的东西：签名里少一个逗号、文本块缩进贴歪、import 少一行、
 * 方法调用与声明的参数个数对不上。这里把渲染出来的六个文件当真实源码交给 javac 走一遍。
 *
 * <p>类路径直接取本模块测试的类路径 —— 它已经含 {@code scaffold-common}（R / PageQuery /
 * PageResult / CsvUtils / BusinessException）、{@code scaffold-framework}
 * （RequiresPermission / OperLog / CsvResponse / SecurityUtils）与 mybatis、spring，
 * 和交付项目里 {@code scaffold-business} 那三个依赖是同一批，不需要再造一份 pom。
 *
 * <p>这里只回答「编不编得过」；「跑起来对不对」是 PR 5G 端到端的事。
 */
class GeneratedCodeCompilesTest {

    @TempDir
    Path tempDir;

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 订单全套六个文件javac编得过() throws Exception {
        compile(GenerationInput.of(project(), model(true, true), richFields()));
    }

    @Test
    void 开关的四种组合都编得过() throws Exception {
        // 审计与逻辑删除各自开关：INSERT / UPDATE / DELETE 三条语句的形状都不同，每种都得能编译
        compile(GenerationInput.of(project(), model(false, false), simpleFields()));
        compile(GenerationInput.of(project(), model(true, false), richFields()));
        compile(GenerationInput.of(project(), model(false, true), richFields()));
    }

    /** 模板 → 交付项目里的源文件相对路径（与 {@code scaffold-business} 的包目录一致） */
    private void compile(Map<String, Object> model) throws Exception {
        String cls = String.valueOf(model.get("className"));
        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("backend/Entity.java.ftl", "domain/" + cls + ".java");
        outputs.put("backend/Row.java.ftl", "domain/" + cls + "Row.java");
        outputs.put("backend/WriteRequest.java.ftl", "domain/" + cls + "WriteRequest.java");
        outputs.put("backend/Mapper.java.ftl", "mapper/" + cls + "Mapper.java");
        outputs.put("backend/Service.java.ftl", "service/" + cls + "Service.java");
        outputs.put("backend/Controller.java.ftl", "controller/" + cls + "Controller.java");

        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        List<String> sources = new ArrayList<>();
        for (Map.Entry<String, String> file : outputs.entrySet()) {
            Path target = sourceRoot.resolve("com/scaffold/business/order/" + file.getValue());
            Files.createDirectories(target.getParent());
            Files.writeString(target, renderer.render(file.getKey(), model), StandardCharsets.UTF_8);
            sources.add(target.toString());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("这条用例需要 JDK 自带的 javac").isNotNull();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-d", classes.toString(), "-classpath", System.getProperty("java.class.path")));
        command.addAll(sources);
        int code = compiler.run(null, null, err, command.toArray(String[]::new));

        // 把编译器的原话连同文件名一起报出来，否则失败只知道「没编过」，得回头一个个猜
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(code).as("javac 退出码").isZero();
    }
}
