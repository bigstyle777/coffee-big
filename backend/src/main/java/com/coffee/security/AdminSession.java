package com.coffee.security;

/**
 * 当前后台请求的登录会话
 *
 * @param adminId  后台账号 ID
 * @param username 登录账号
 * @param tokenId  JWT 唯一标识
 */
public record AdminSession(Long adminId, String username, String tokenId) {
}
