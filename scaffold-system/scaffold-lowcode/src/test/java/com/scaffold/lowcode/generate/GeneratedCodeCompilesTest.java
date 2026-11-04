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
import java.util.List;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 产物真编译（PR 5B 的验收项，PR 5E 起改吃 {@link ModelCodeGenerator} 的出参）。
 *
 * <p>形状断言抓不到编译器才抓得到的东西：签名里少一个逗号、文本块缩进贴歪、import 少一行、
 * 方法调用与声明的参数个数对不上。这里把渲染出来的源码文件按交付项目里的真实路径落盘，再交给 javac 走一遍。
 *
 * <p>PR 5E 加进来的这条最关键：B 档 {@code Service} 现在会调 C 档 {@code Extension} 的七个钩子，
 * 「调用点与声明对不对得上」只有编译器说了算 —— 这正是分档设计的代价（方案 §5.2：签名是平台契约，
 * 改名或删方法要在下次发布时直接编译失败）。
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

    private final ModelCodeGenerator generator = new ModelCodeGenerator(new TemplateRenderer());

    @Test
    void 订单全套七个java文件编得过() throws Exception {
        compile(generator.renderFiles(model(true, true), richFields()));
    }

    @Test
    void 开关的四种组合都编得过() throws Exception {
        // 审计与逻辑删除各自开关：INSERT / UPDATE / DELETE 三条语句的形状都不同，每种都得能编译
        compile(generator.renderFiles(model(false, false), simpleFields()));
        compile(generator.renderFiles(model(true, false), richFields()));
        compile(generator.renderFiles(model(false, true), richFields()));
    }

    private void compile(List<GeneratedFile> files) throws Exception {
        List<GeneratedFile> sources = files.stream().filter(file -> file.path().endsWith(".java")).toList();
        assertThat(sources).as("一个模型的后端产物").hasSize(7);

        Path sourceRoot = Files.createDirectories(tempDir.resolve("src"));
        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        // 按交付项目里的真实路径落盘，顺带验证 GeneratedFileWriter 的路径处理
        GeneratedFileWriter.write(sourceRoot, sources);
        List<String> paths = sources.stream().map(file -> sourceRoot.resolve(file.path()).toString()).toList();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("这条用例需要 JDK 自带的 javac").isNotNull();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> command = new ArrayList<>(List.of("-proc:none", "-encoding", "UTF-8",
                "-d", classes.toString(), "-classpath", System.getProperty("java.class.path")));
        command.addAll(paths);
        int code = compiler.run(null, null, err, command.toArray(String[]::new));

        // 把编译器的原话连同文件名一起报出来，否则失败只知道「没编过」，得回头一个个猜
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(code).as("javac 退出码").isZero();
    }
}
