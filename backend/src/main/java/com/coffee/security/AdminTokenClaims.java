package com.coffee.security;

import java.time.LocalDateTime;

/**
 * admin-token 解析结果
 *
 * @param adminId   后台账号 ID
 * @param username  登录账号
 * @param tokenId   JWT 唯一标识
 * @param expiresAt 过期时间
 */
public record AdminTokenClaims(Long adminId, String username, String tokenId, LocalDateTime expiresAt) {
}
