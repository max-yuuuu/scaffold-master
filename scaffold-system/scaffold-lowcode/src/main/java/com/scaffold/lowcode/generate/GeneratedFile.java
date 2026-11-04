package com.scaffold.lowcode.generate;

/**
 * 一份生成产物：落到交付项目的哪个路径、内容是什么、归哪一档（方案 §5.2）。
 *
 * @param path    相对交付项目根的路径，用 {@code /} 分隔
 * @param content 渲染好的文本
 * @param tier    所有权档，决定发布时是覆盖还是「存在即跳过」
 */
public record GeneratedFile(String path, String content, Tier tier) {

    /**
     * 按路径建一份产物，档位由 {@link GeneratedPaths#tier} 反推 —— 生成器不能自己声明「我是 C 档」，
     * 落到 A 档或不认识的路径上时这里当场拒绝，越界的模板到不了磁盘。
     */
    public static GeneratedFile of(String path, String content) {
        return new GeneratedFile(path, content, GeneratedPaths.tier(path));
    }

    /**
     * 生成物只有 B、C 两档：A 档固定代码在项目初始化时一次性写入，不归生成器管。
     */
    public enum Tier {
        /** 纯生成：每次发布整文件覆盖，约定人不改 */
        GENERATED,
        /** 扩展点骨架：首次生成后归人工所有，之后发布跳过 */
        EXTENSION
    }
}
