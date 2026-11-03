package com.scaffold.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** 令牌中的用户ID声明（userId），与 LoginUser.userId 对应 */
    private static final String USER_ID_CLAIM = "userId";

    /** 令牌中的租户ID声明（tenantId），与 LoginUser.tenantId 对应；多租户下所有业务查询据此隔离 */
    private static final String TENANT_ID_CLAIM = "tenantId";

    private final String secret;
    private final long expiresSeconds;

    public JwtService(
            @Value("${scaffold.jwt.secret}") String secret,
            @Value("${scaffold.jwt.expires-seconds}") long expiresSeconds
    ) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET 必须配置为至少 32 字节的非空白密钥");
        }
        this.secret = secret;
        this.expiresSeconds = expiresSeconds;
    }

    public String generateToken(Long userId, String username, Long tenantId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .claim(TENANT_ID_CLAIM, tenantId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresSeconds)))
                .signWith(secretKey())
                .compact();
    }

    public String parseUsername(String token) {
        LoginUser principal = parsePrincipal(token);
        return principal == null ? null : principal.username();
    }

    /**
     * 解析令牌中的登录主体（用户 ID + 账号 + 租户 ID）；令牌无效时返回 null。
     * userId / tenantId 声明在旧版本令牌中可能缺失，此时为 null，由下游按业务规则处理。
     */
    public LoginUser parsePrincipal(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    readLong(claims.get(USER_ID_CLAIM)),
                    claims.getSubject(),
                    readLong(claims.get(TENANT_ID_CLAIM))
            );
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT解析失败: {}", ex.getMessage());
            return null;
        }
    }

    public long expiresSeconds() {
        return expiresSeconds;
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Long readLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
