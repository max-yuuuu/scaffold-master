package com.scaffold.lowcode.repo;

import com.scaffold.common.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 仓库地址白名单校验。项目要往这个地址推代码，PR 4 的 JGit 直接拿这个值建 remote，
 * 所以这里就是安全边界：只放行 {@code https://host/path} 与 {@code git@host:path} 两种形态，
 * 拒掉 {@code file://}、{@code ext::}、{@code ssh://}、裸路径等能让本机文件或外部命令执行的写法，
 * 也拒掉 URL 里内嵌账号口令 —— 凭据只走主机 SSH 密钥，不进数据库（方案 §〇之二 第 4 条）。
 */
public final class RepoUrlValidator {

    private static final int MAX_LENGTH = 256;
    private static final String HTTPS_PREFIX = "https://";
    private static final String SCP_PREFIX = "git@";

    /** https://host[:port]/path[/sub][.git]，host 必须带点，路径至少一段 */
    private static final Pattern HTTPS = Pattern.compile(
            "^https://[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(:\\d{2,5})?/[A-Za-z0-9._/-]+(?:\\.git)?/?$");

    /** git@host:path[/sub][.git]（SSH 的 SCP 写法） */
    private static final Pattern SCP = Pattern.compile(
            "^git@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}:[A-Za-z0-9._/-]+(?:\\.git)?/?$");

    private RepoUrlValidator() {
    }

    /**
     * 校验并返回去掉首尾空白后的仓库地址。
     *
     * @param raw 用户填写的仓库地址（lc_project.repo_url）
     * @throws BusinessException 形态不在白名单内
     */
    public static String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            throw new BusinessException("仓库地址不能为空");
        }
        if (url.length() > MAX_LENGTH) {
            throw new BusinessException("仓库地址长度不能超过 " + MAX_LENGTH);
        }
        if (url.startsWith(HTTPS_PREFIX)) {
            requireNoCredentials(url.substring(HTTPS_PREFIX.length()).split("/")[0]);
            requireMatch(HTTPS, url);
        } else if (url.startsWith(SCP_PREFIX)) {
            requireNoCredentials(url.substring(SCP_PREFIX.length()));
            requireMatch(SCP, url);
        } else {
            throw new BusinessException("仓库地址只支持 https:// 或 git@host:path 两种形式");
        }
        return url;
    }

    /** 未绑定仓库时允许为空，由调用方决定是提示还是拒绝 */
    public static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }

    private static void requireNoCredentials(String authorityOrPath) {
        if (authorityOrPath.contains("@")) {
            throw new BusinessException("仓库地址不能内嵌账号或令牌，推送凭据走主机 SSH 密钥");
        }
    }

    private static void requireMatch(Pattern pattern, String url) {
        if (!pattern.matcher(url).matches()) {
            throw new BusinessException("仓库地址格式不正确：需要 协议://域名/组织/仓库 或 git@域名:组织/仓库");
        }
    }
}
