package com.scaffold.lowcode.workspace;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 删除文件里的平台专属标记块：从含 {@code scaffold:platform-only:start} 的行起跳过，
 * 到含 {@code scaffold:platform-only:end} 的行止（两端行一并删除）。
 *
 * <p>标记只用注释写在各文件自己的语法里（XML 用 {@code <!-- -->}、SQL 用 {@code --}、
 * TS/Java 用 {@code //}、YAML 用 {@code #}），这里只认关键字，因此一套规则通吃所有文本文件。
 */
public final class PlatformOnlyStripper {

    private static final String START = ScaffoldTrimPolicy.PLATFORM_ONLY_TOKEN + ":start";
    private static final String END = ScaffoldTrimPolicy.PLATFORM_ONLY_TOKEN + ":end";

    private PlatformOnlyStripper() {
    }

    /** 二进制内容（含 NUL）原样返回，不做文本处理 */
    public static byte[] strip(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.contains(ScaffoldTrimPolicy.PLATFORM_ONLY_TOKEN)) {
            return content;
        }
        List<String> kept = stripLines(Arrays.asList(text.split("\n", -1)));
        return String.join("\n", kept).getBytes(StandardCharsets.UTF_8);
    }

    static List<String> stripLines(List<String> lines) {
        List<String> kept = new ArrayList<>(lines.size());
        int unclosed = 0;
        for (String line : lines) {
            if (line.contains(START) && line.contains(END)) {
                throw new IllegalStateException("标记块必须独占整行，不能在同行里成对出现：" + line.trim());
            }
            if (line.contains(START)) {
                unclosed++;
                continue;
            }
            if (line.contains(END)) {
                if (unclosed == 0) {
                    throw new IllegalStateException("裁剪标记块没有对应的 start：" + line.trim());
                }
                unclosed--;
                continue;
            }
            if (unclosed == 0) {
                kept.add(line);
            }
        }
        if (unclosed > 0) {
            throw new IllegalStateException("裁剪标记块缺少 end，剩余 " + unclosed + " 个未闭合");
        }
        return kept;
    }
}
