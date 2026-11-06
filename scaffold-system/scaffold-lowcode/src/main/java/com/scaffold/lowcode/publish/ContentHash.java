package com.scaffold.lowcode.publish;

import com.scaffold.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 产物内容的 sha256：版本快照按它记，回滚按它对账 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new BusinessException("算不出产物哈希：" + e.getMessage());
        }
    }
}
