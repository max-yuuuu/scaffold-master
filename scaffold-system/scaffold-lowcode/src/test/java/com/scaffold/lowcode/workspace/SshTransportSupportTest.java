package com.scaffold.lowcode.workspace;

import org.apache.sshd.common.util.security.SecurityUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SSH 传输的两个运行期前提（真机验证时一个个踩出来的）。
 *
 * <p>这两条都不是逻辑 bug，而是「环境没问题、依赖缺了或代理劫持了，报错却含糊」那一类：
 * 缺 BouncyCastle 时报 {@code publickey: no keys to try}，被本机 HTTP 代理劫持时报
 * {@code Session is being closed}。放到测试里钉住，比写进部署文档更容易被守住。
 */
class SshTransportSupportTest {

    @Test
    void ed25519部署密钥需要BouncyCastle注册() {
        // Apache SSHD 的 OpenSSH 私钥解析器认不了 ssh-ed25519，除非 BC provider 已注册
        assertThat(SecurityUtils.isBouncyCastleRegistered())
                .as("缺 bcprov 时 SSHD 报 Unsupported key type (ssh-ed25519)，认证阶段直接 no keys to try")
                .isTrue();

        // 签名算法本身要可用，否则即使解出密钥也无法完成 publickey 认证
        assertThatCode(() -> java.security.Signature.getInstance("Ed25519"))
                .doesNotThrowAnyException();
    }

    @Test
    void git走ssh不受本机HTTP代理影响() {
        // 返回 null 表示直连：代理只该由 ~/.ssh/config 的 ProxyCommand 决定，
        // 不能交给 JGit 的 DefaultProxyDataFactory —— 它会读 JVM/系统代理，把 github.com:22
        // 指到本机代理端口上，失败信息只剩一句 "Session is being closed"
        assertThat(JGitGateway.directSshProxy().get(new InetSocketAddress("github.com", 22))).isNull();
        assertThat(JGitGateway.directSshProxy().get(new InetSocketAddress("10.0.0.1", 22))).isNull();
    }
}
