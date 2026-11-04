package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成产物落进交付项目工作区（方案 §4.4 的两条 sink 之一，另一条见 {@link GeneratedFileZip}）。
 *
 * <p>覆盖还是跳过完全按档位来，不看内容也不看时间：
 * <ul>
 *   <li>B 档整文件覆盖 —— 约定人工不改它，改了也会被冲掉（冲掉的内容在合并请求的 diff 里看得见）；</li>
 *   <li>C 档「存在即跳过」—— 骨架第一次发布时写进去，之后就归人工，平台再也不碰。</li>
 * </ul>
 *
 * <p><b>C 档这条依赖一个前提：调用方给的工作区得反映项目当前的内容。</b>发布链路写的是发布分支，
 * 而主干上人工改过的扩展类要通过 checkout 或合并落到这份工作区里，跳过判断才认得出「这份已经存在」。
 * 前提没满足时骨架会被重新生成，两边在合并请求里以「both added」的形式冲突暴露出来 —— 不会静默丢代码，
 * 但要人自己取舍，所以 PR 7 组工作区时要把这条做对。
 */
public final class GeneratedFileWriter {

    private GeneratedFileWriter() {
    }

    /** 这一份产物这次是新建、覆盖，还是因为属于 C 档且已存在而跳过 */
    public enum Action {
        CREATED, OVERWRITTEN, SKIPPED
    }

    public record Written(String path, Action action) {
    }

    /**
     * @param workspace 交付项目工作区根目录（一个 Git 仓库的 checkout）
     * @return 每个产物一行结果，顺序与入参一致，供发布日志与 {@code sync_message} 汇总
     */
    public static List<Written> write(Path workspace, List<GeneratedFile> files) {
        List<Written> results = new ArrayList<>();
        for (GeneratedFile file : files) {
            Path target = workspace.resolve(file.path()).normalize();
            if (!target.startsWith(workspace)) {
                // 路径是模板按模型编码拼出来的，越界只可能来自编码里的 ../ 或绝对路径，一律停手
                throw new BusinessException("生成产物的路径越出工作区：" + file.path());
            }
            boolean exists = Files.exists(target);
            if (file.tier() == GeneratedFile.Tier.EXTENSION && exists) {
                results.add(new Written(file.path(), Action.SKIPPED));
                continue;
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BusinessException("写入生成产物失败：" + file.path() + "（" + e.getMessage() + "）");
            }
            results.add(new Written(file.path(), exists ? Action.OVERWRITTEN : Action.CREATED));
        }
        return results;
    }
}
