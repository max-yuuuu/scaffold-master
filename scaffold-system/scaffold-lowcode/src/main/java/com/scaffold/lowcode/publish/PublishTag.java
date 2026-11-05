package com.scaffold.lowcode.publish;

import com.scaffold.common.exception.BusinessException;
import org.eclipse.jgit.lib.Repository;

/**
 * 发布版本标记（方案 §5.1：发布成功打 tag，作为合并与回滚的锚点）。
 *
 * <p>名字里带项目与页面编码而不是只带版本号，是因为一个租户仓库里同时住着多个项目的多个页面：
 * 只写 {@code v3} 的话，合并请求与 PR 9 的三级回滚都要先猜这个 3 属于谁。
 *
 * <p>tag 一旦推出去就只创建、不移动、不删除 —— 与底座标记 tag（{@link com.scaffold.lowcode.workspace.PlatformBaseTag}）
 * 同一条规矩：移动它等于改掉那一版的锚点，回滚会把内容回滚到错的那一份。
 */
public final class PublishTag {

    private PublishTag() {
    }

    public static String name(String projectCode, String pageCode, int version) {
        String name = "v" + projectCode + "-" + pageCode + "-" + version;
        // Git 的 refname 规则比看起来严（不许 ..、不许空格、不许以 . 结尾）。项目编码与页面编码在新增时
        // 各有一套校验，但那两套是为「包名 / 文件名」定的，这里按 Git 自己的规则再判一次：
        // 一个建不出来的 tag 名要在推送之后才发现，那次发布已经推到分支上去了。
        if (!Repository.isValidRefName("refs/tags/" + name)) {
            throw new BusinessException("版本号标记名不合法，发布不出去：" + name
                    + "（项目编码与页面编码只能由字母、数字、- _ . 组成）");
        }
        return name;
    }

    public static String message(String projectCode, String pageCode, int version, String commitSha) {
        return "元搭低代码平台发布 " + projectCode + "/" + pageCode + " v" + version + "（提交 " + commitSha + "）";
    }
}
