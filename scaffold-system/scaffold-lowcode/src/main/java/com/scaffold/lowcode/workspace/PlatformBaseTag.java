package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

/**
 * 底座标记 tag：把「平台是在哪个底座版本上交付这份固定代码的」钉进项目仓库本身（方案 §5.2）。
 *
 * <p>为什么非得有这个 tag：底座升级走三方合并，base 侧要的是「当初交付那一版 A 档的内容」。
 * 库里 {@code lc_project.base_scaffold_sha} 只是一个字符串，{@code last_commit_sha} 又每次发布都被覆盖 ——
 * 光有 sha 拿不到内容，而项目仓库里那个交付提交过一阵就没人认得出是哪个了。tag 指向它，
 * base 侧就能直接从项目仓库读，不必让平台去翻底座仓库的历史。
 *
 * <p>规则：只创建，永不移动、永不删除。每交付一份新的 A 档快照就打一个新 tag（底座 sha 不同，
 * 名字自然不同）；同名 tag 已存在说明这个底座版本交付过了，原样留着 —— 移动它等于把上一轮
 * 升级的 base 改掉，三方合并会算出错的结果。
 */
public final class PlatformBaseTag {

    /** 命名空间前缀，与项目自己的 tag 隔开 */
    public static final String PREFIX = "yuanda/platform-base/";

    private PlatformBaseTag() {
    }

    /**
     * 底座提交号 → tag 名。提交号会被拼进 Git 引用名并推到使用方仓库，所以这里挡一次格式。
     *
     * @param baseScaffoldSha 底座 checkout 的提交号
     */
    public static String name(String baseScaffoldSha) {
        if (baseScaffoldSha == null || !baseScaffoldSha.matches("^[0-9a-fA-F]{7,64}$")) {
            throw new BusinessException("底座提交号不可用，无法生成底座标记 tag：" + baseScaffoldSha);
        }
        return PREFIX + baseScaffoldSha;
    }

    /** 附注 tag 的说明：让在项目仓库里看到它的人知道这东西不能动 */
    public static String message(String baseScaffoldSha) {
        return "元搭低代码平台底座快照 " + baseScaffoldSha + "\n\n"
                + "指向平台交付本项目固定代码（A 档）时的快照提交，是后续「底座升级」三方合并的 base 侧。\n"
                + "请勿移动或删除本 tag，平台自己也不会改它。\n";
    }
}
