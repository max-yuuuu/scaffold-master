package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 生成产物打成 zip（方案 §4.4 的两条 sink 之一，另一条见 {@link GeneratedFileWriter}）。
 *
 * <p>给人手工取产物用的：没绑定仓库的项目、想先看看生成结果长什么样的时候。
 * 压缩包里的目录就是交付项目里的目录，解压到项目根即为一次覆盖 —— 因此 zip 里
 * <b>照样带着 C 档骨架</b>，下载方要不要覆盖自己那份扩展点由人判断，平台在这条路上不做跳过。
 */
public final class GeneratedFileZip {

    private GeneratedFileZip() {
    }

    public static byte[] pack(List<GeneratedFile> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (GeneratedFile file : files) {
                    zip.putNextEntry(new ZipEntry(file.path()));
                    zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("代码压缩包生成失败：" + e.getMessage());
        }
    }
}
