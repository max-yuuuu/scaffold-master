package com.scaffold.lowcode.repo;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库地址白名单：这是 PR 4 推送链路的输入边界，非法形态必须在这里挡掉，
 * 不能让 file:// / ext:: 之类的写法走到 JGit。
 */
class RepoUrlValidatorTest {

    @Test
    void 放行两种标准形态() {
        assertThat(RepoUrlValidator.normalize("https://github.com/acme/order-service.git"))
                .isEqualTo("https://github.com/acme/order-service.git");
        assertThat(RepoUrlValidator.normalize("  https://gitlab.example.com/team/app  "))
                .isEqualTo("https://gitlab.example.com/team/app");
        assertThat(RepoUrlValidator.normalize("https://gitee.com/acme/app.git")).isEqualTo("https://gitee.com/acme/app.git");
        assertThat(RepoUrlValidator.normalize("git@github.com:acme/order-service.git"))
                .isEqualTo("git@github.com:acme/order-service.git");
        assertThat(RepoUrlValidator.normalize("git@git.internal.example.com:team/app")).isEqualTo("git@git.internal.example.com:team/app");
    }

    @Test
    void 拒绝能读写本机文件或执行外部命令的形态() {
        assertRejected("file:///home/git/repo.git");
        assertRejected("ext::sh -c 'whoami'");
        assertRejected("ssh://git@github.com/acme/app.git");
        assertRejected("/var/git/repo.git");
        assertRejected("./repo");
        assertRejected("github.com/acme/app");
    }

    @Test
    void 拒绝内嵌凭据与不安全协议() {
        assertRejected("https://token:x-oauth-basic@github.com/acme/app.git");
        assertRejected("https://admin:pass@git.example.com/a/b.git");
        assertRejected("http://github.com/acme/app.git");
        assertRejected("git@github.com:evil@example.com:a/b.git");
    }

    @Test
    void 拒绝空值与畸形地址() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected("https://");
        assertRejected("https://github.com");
        assertRejected("https://github.com/");
        assertRejected("git@github.com:");
        assertRejected("https://host-no-tld/app/repo");
        assertRejected("https://github.com/acme/" + "x".repeat(256));
    }

    private static void assertRejected(String raw) {
        assertThatThrownBy(() -> RepoUrlValidator.normalize(raw))
                .as("地址 %s 应被拒绝", raw)
                .isInstanceOf(BusinessException.class);
    }
}
