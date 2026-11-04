package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 定位底座源码 checkout（= 本仓库的工作副本）。
 *
 * <p>交付快照是从这份 checkout 里按裁剪清单读出来的，所以运行平台的主机（或容器）必须能访问它。
 * 本地开发时从当前目录向上找即可；容器部署要把 checkout 挂进容器并显式配置
 * {@code scaffold.lowcode.scaffold-source-root}。
 */
public final class ScaffoldSourceLocator {

    /** 判定「这就是底座根」的两个文件，缺一个都不算 */
    private static final String[] SIGNATURE = {"scaffold-system/pom.xml", "scaffold-vue/package.json"};

    private ScaffoldSourceLocator() {
    }

    /**
     * @param configured {@code scaffold.lowcode.scaffold-source-root} 的值，留空表示自动探测
     */
    public static Path locate(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path explicit = Paths.get(configured.trim()).toAbsolutePath().normalize();
            if (!isScaffoldRoot(explicit)) {
                throw new BusinessException("底座源码目录不对，找不到 " + String.join(" 与 ", SIGNATURE) + "：" + explicit);
            }
            return explicit;
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (Path dir = current; dir != null; dir = dir.getParent()) {
            if (isScaffoldRoot(dir)) {
                return dir;
            }
        }
        throw new BusinessException("未能自动定位底座源码目录（从 " + current + " 向上查找）。"
                + "请设置 scaffold.lowcode.scaffold-source-root 指向本仓库的 checkout");
    }

    private static boolean isScaffoldRoot(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        for (String signature : SIGNATURE) {
            if (!Files.isRegularFile(dir.resolve(signature))) {
                return false;
            }
        }
        return true;
    }
}
