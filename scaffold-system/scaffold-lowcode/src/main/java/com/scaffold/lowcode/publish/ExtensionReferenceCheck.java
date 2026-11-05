package com.scaffold.lowcode.publish;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布前的 C 档引用体检（方案 §5.2「C 档引用体检」第 2 条）。
 *
 * <p>挡的是这一件事故：模型删了字段，人写的 {@code {Cls}Extension} 还在引用它。只靠交付项目编译 +
 * 合并请求的 diff 太晚也太吵 —— 等编译失败时人已经在合并请求里了，而 diff 只会显示「生成物变了」，
 * 不会告诉他「你少了一个字段」。
 *
 * <p><b>判据是本次发布真正生成的那几个类里声明的成员，不是 {@code lc_field} 的字段清单</b>：主键与审计列
 * 由模板补在 Row / Entity 上而不在字段表里，按字段清单判就会把 {@code row.getCreateTime()} 报成错引用。
 * 一个天天误报的检查用不了两次就没人肯当真，所以这里把「合法」定义为「生成物里真有其名」。
 *
 * <p><b>剩下的噪音是文档明确接受的代价</b>（§5.2 第 3 条「宁多报不漏报」）：正则级检查分不清
 * {@code type.getName()} 里的 name 是人删掉的字段还是一个 JDK 对象的读取。所以命中的东西只列清单、
 * 由操作者显式 {@code force} 放行，而不是平台自作主张地猜哪个是假的。不引 Java AST 解析器的理由同上 ——
 * 目标是把高频事故提前五分钟暴露，编译器仍是最后防线，不是第一道。
 */
public final class ExtensionReferenceCheck {

    /** 一条可疑引用：文件、行号、当成字段名的符号，以及那一行原文（人要判断是不是误报，得看到上下文） */
    public record Stale(String path, int line, String field, String text) {
    }

    /** {@code foo.getX()} / {@code foo.isX()}：无参读取。接收方只能是小写标识符或一个调用结果 */
    private static final Pattern GETTER = Pattern.compile("\\b[a-z]\\w*\\s*\\.\\s*(?:get|is)([A-Z]\\w*)\\s*\\(\\s*\\)");

    /** {@code foo.setX(...)}：写入，参数个数不看 */
    private static final Pattern SETTER = Pattern.compile("\\b[a-z]\\w*\\s*\\.\\s*set([A-Z]\\w*)\\s*\\(");

    /**
     * {@code foo.bar}：成员直接取。后面不能紧跟 {@code (}（那是方法调用）或标识符字符。
     *
     * <p>接收方限定小写开头是有意的：{@code System.out}、{@code Boolean.TRUE} 这类类名限定的静态访问
     * 不是模型字段；而 {@code )} 之后的链式调用（{@code rows.get(0).amount}）要算进来。
     */
    private static final Pattern FIELD_ACCESS = Pattern.compile("(?:\\b[a-z]\\w*|\\))\\s*\\.\\s*([a-z]\\w*)\\s*(?!\\w|\\()");

    /** 生成类里的成员：字段声明与 getter 都算「真有其名」 */
    private static final Pattern MEMBER_FIELD = Pattern.compile("\\bprivate\\s+[\\w<>,.\\[\\]\\s]+?\\s(\\w+)\\s*[;=]");
    private static final Pattern MEMBER_GETTER = Pattern.compile("\\bpublic\\s+[\\w<>,.\\[\\]\\s]+?\\sget([A-Z]\\w*)\\s*\\(");

    /** 人自己在扩展点里声明的方法与字段：那是这个文件的私有词汇，不该被当成模型字段 */
    private static final Pattern DECLARED = Pattern.compile(
            "\\b(?:public|private|protected|final|var)\\s+[\\w<>,.\\[\\]\\s]*?\\s(\\w+)\\s*[;=(]");

    /** {@code .class} 是类字面量的关键字，任何语言里都不是字段 */
    private static final Set<String> IGNORED = Set.of("class");

    private ExtensionReferenceCheck() {
    }

    /**
     * @param sources 本次发布生成的 Row / Entity / WriteRequest 等 B 档源码原文
     * @return 这些类里声明的成员名（首字母小写的形式），即扩展点可以合法引用的全部词汇
     */
    public static Set<String> declaredMembers(String... sources) {
        Set<String> names = new LinkedHashSet<>();
        for (String source : sources) {
            collect(MEMBER_FIELD, source, names, true);
            collect(MEMBER_GETTER, source, names, false);
        }
        return names;
    }

    /**
     * @param path    报给人看的文件路径（工作区相对路径）
     * @param source  C 档文件原文；文件还不存在时调用方不必调过来
     * @param allowed 生成物之外还允许出现的名字（比如同一模块里人自己写的别的类）
     * @param members {@link #declaredMembers} 的结果
     */
    public static List<Stale> scan(String path, String source, Set<String> allowed, Set<String> members) {
        Set<String> legal = new LinkedHashSet<>(members);
        legal.addAll(allowed);
        legal.addAll(selfDeclared(source));

        List<Stale> hits = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            // package 与 import 里的包名段长得和「对象.字段」一模一样（com.scaffold 就是「com 的 scaffold」），
            // 而任何一份 java 都有这两行：不排掉的话每一次发布都会被自己的清单拦下来
            String line = lines[i].trim();
            if (line.startsWith("package ") || line.startsWith("import ")) {
                continue;
            }
            Set<String> found = new LinkedHashSet<>();
            collectRaw(GETTER, lines[i], found, true);
            collectRaw(SETTER, lines[i], found, true);
            collectRaw(FIELD_ACCESS, lines[i], found, false);
            for (String field : found) {
                if (!legal.contains(field) && !IGNORED.contains(field)) {
                    hits.add(new Stale(path, i + 1, field, lines[i].trim()));
                }
            }
        }
        return List.copyOf(hits);
    }

    /** 体检结果里给人读的一句：带上文件名、行号与字段名，设计器与命令行都照它显示 */
    public static String describe(List<Stale> hits) {
        StringBuilder text = new StringBuilder("扩展点里还在引用模型已不再产出的字段：");
        hits.forEach(hit -> text.append("\n  ").append(hit.path()).append(":").append(hit.line())
                .append(" 引用了 ").append(hit.field()).append("（").append(hit.text()).append("）"));
        text.append("\n  可能是同名局部变量或注释；确认过再发布就带 force，编译器是最后一道防线。");
        return text.toString();
    }

    private static Set<String> selfDeclared(String source) {
        Set<String> names = new LinkedHashSet<>();
        collect(DECLARED, source, names, true);
        return names;
    }

    private static void collect(Pattern pattern, String text, Set<String> sink, boolean camelCase) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            sink.add(camelCase ? camelCase(matcher.group(1)) : camelCase(matcher.group(1)));
        }
    }

    private static void collectRaw(Pattern pattern, String line, Set<String> sink, boolean upperFirst) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String name = matcher.group(1);
            sink.add(upperFirst ? camelCase(name) : name);
        }
    }

    /** {@code OrderNo} → {@code orderNo}；{@code amount} 原样 */
    private static String camelCase(String name) {
        if (name.isEmpty()) {
            return name;
        }
        // 连续大写是缩写（如 getXML）：Java 的驼峰不会把它整个降下来，按原形收
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
