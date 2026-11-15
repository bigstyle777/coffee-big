package com.coffee.security;

import java.time.LocalDateTime;

/**
 * 新签发的 admin-token
 *
 * @param token      JWT 字符串
 * @param tokenId    JWT 唯一标识，用于 Redis 白名单与退出登录
 * @param expiresAt  过期时间
 */
public record AdminToken(String token, String tokenId, LocalDateTime expiresAt) {
}
