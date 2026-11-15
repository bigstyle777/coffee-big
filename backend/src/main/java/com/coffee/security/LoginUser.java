package com.coffee.security;

/**
 * 登录用户上下文快照，由 JWT 解析得到
 *
 * @param userId 用户 ID
 * @param role   角色：CUSTOMER / ADMIN
 */
public record LoginUser(Long userId, String role) {
}
