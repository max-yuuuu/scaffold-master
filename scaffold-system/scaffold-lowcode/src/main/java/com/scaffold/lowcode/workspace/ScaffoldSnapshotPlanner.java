package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 按 {@link ScaffoldTrimPolicy} 把底座 checkout 读成一份交付快照（方案 §6.4）。
 *
 * <p>计划阶段就做校验：底座结构与清单不一致、标记块没闭合、裁剪后仍残留平台引用，都在这里当场失败，
 * 而不是把带病的产物推到租户仓库后再由人发现。
 */
public final class ScaffoldSnapshotPlanner {

    /** 参与平台引用扫描的源码与配置后缀；文档类 .md 里出现历史描述不算残留 */
    private static final List<String> SCANNED_CODE_SUFFIXES = List.of(
            ".java", ".ts", ".vue", ".js", ".json", ".xml", ".yml", ".yaml", ".conf", ".properties", ".example", ".html");

    private ScaffoldSnapshotPlanner() {
    }

    /**
     * @param sourceRoot           底座 checkout 根（本仓库的工作副本，含 {@code scaffold-system/} 与 {@code scaffold-vue/}）
     * @param scaffoldUiVersion    交付项目里 {@code @yuanda-lowcode-ai/scaffold-ui} 的依赖写法（版本号或 {@code file:} 路径），
     *                             空则用 {@link ScaffoldTrimPolicy#DEFAULT_SCAFFOLD_UI_VERSION}
     * @throws BusinessException 底座结构与裁剪清单不一致
     */
    public static ScaffoldSnapshot plan(Path sourceRoot, String scaffoldUiVersion) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            throw new BusinessException("底座源码目录不存在：" + sourceRoot + "，请检查 scaffold.lowcode.scaffold-source-root 配置");
        }
        String version = scaffoldUiVersion == null || scaffoldUiVersion.isBlank()
                ? ScaffoldTrimPolicy.DEFAULT_SCAFFOLD_UI_VERSION
                : scaffoldUiVersion.trim();
        List<String> missing = new ArrayList<>();
        for (String required : ScaffoldTrimPolicy.requiredRoots()) {
            if (!Files.isRegularFile(sourceRoot.resolve(required))) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("底座源码与裁剪清单不一致，缺少：" + String.join("、", missing));
        }
        List<SnapshotFile> files = collect(sourceRoot, version);
        if (files.isEmpty()) {
            throw new BusinessException("裁剪清单没有命中任何文件：" + sourceRoot);
        }
        validate(files);
        return new ScaffoldSnapshot(List.copyOf(files));
    }

    private static List<SnapshotFile> collect(Path sourceRoot, String version) {
        List<SnapshotFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String relative = relativize(sourceRoot, dir);
                    return relative.isEmpty() || ScaffoldTrimPolicy.descends(relative)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relative = relativize(sourceRoot, file);
                    if (!ScaffoldTrimPolicy.accepts(relative)) {
                        return FileVisitResult.CONTINUE;
                    }
                    byte[] raw = Files.readAllBytes(file);
                    byte[] stripped;
                    try {
                        stripped = PlatformOnlyStripper.strip(raw);
                    } catch (IllegalStateException e) {
                        throw new BusinessException("裁剪标记块写法有误：" + relative + " → " + e.getMessage());
                    }
                    byte[] delivered = substituteForDelivery(relative, stripped, version);
                    if (delivered != null) {
                        files.add(SnapshotFile.rewritten(relative, delivered));
                    } else if (stripped == raw) {
                        files.add(SnapshotFile.verbatim(relative, file));
                    } else {
                        files.add(SnapshotFile.rewritten(relative, stripped));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException("读取底座源码失败：" + e.getMessage());
        }
        return files;
    }

    /**
     * 按 {@link ScaffoldTrimPolicy#deliverySubstitutions()} 把「平台怎么构建」那几行换成「交付项目怎么构建」。
     *
     * @return 改写后的字节；该文件没有任何改写块时返回 {@code null}，表示按原文件直接拷贝
     * @throws BusinessException 改写块没找到或被改成多处 —— 宁可不交付，也不交付一个装不上依赖的项目
     */
    private static byte[] substituteForDelivery(String relative, byte[] content, String version) {
        List<ScaffoldTrimPolicy.DeliverySubstitution> applicable = ScaffoldTrimPolicy.deliverySubstitutions().stream()
                .filter(item -> item.path().equals(relative))
                .toList();
        if (applicable.isEmpty()) {
            return null;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        for (ScaffoldTrimPolicy.DeliverySubstitution item : applicable) {
            String from = String.join("\n", item.from());
            String to = String.join("\n", item.to()).replace(ScaffoldTrimPolicy.SCAFFOLD_UI_VERSION_PLACEHOLDER, version);
            int hits = countOccurrences(text, from);
            if (hits != 1) {
                throw new BusinessException("交付改写失败：" + relative + " 里应当恰好出现 1 次「" + item.from().get(0).strip()
                        + "」开头的片段，实际 " + hits + " 次 —— 底座的构建写法改过了，要先同步 ScaffoldTrimPolicy 的交付改写表");
            }
            text = text.replace(from, to);
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static void validate(List<SnapshotFile> files) {
        for (SnapshotFile file : files) {
            String lower = file.path().toLowerCase(Locale.ROOT);
            List<String> tokens;
            if (lower.endsWith(".sql")) {
                tokens = ScaffoldTrimPolicy.forbiddenSchemaReferences();
            } else if (SCANNED_CODE_SUFFIXES.stream().anyMatch(lower::endsWith)) {
                tokens = ScaffoldTrimPolicy.forbiddenCodeReferences();
            } else {
                continue;
            }
            String text = new String(content(file), StandardCharsets.UTF_8);
            for (String token : tokens) {
                if (text.contains(token)) {
                    throw new BusinessException("裁剪清单遗漏：交付文件 " + file.path() + " 仍引用 " + token);
                }
            }
        }
    }

    /** 供校验与测试读取最终内容 */
    public static byte[] content(SnapshotFile file) {
        if (file.content() != null) {
            return file.content();
        }
        try {
            return Files.readAllBytes(file.copyFrom());
        } catch (IOException e) {
            throw new BusinessException("读取交付文件失败：" + file.path());
        }
    }

    private static String relativize(Path root, Path path) {
        if (path.equals(root)) {
            return "";
        }
        return root.relativize(path).toString().replace('\\', '/');
    }
}
