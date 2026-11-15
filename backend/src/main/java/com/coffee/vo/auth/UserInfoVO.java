package com.coffee.vo.auth;

/**
 * 登录用户信息
 */
public record UserInfoVO(
        Long id,
        String nickname,
        String avatar,
        String phone,
        String role
) {
}
