package com.coffee.vo.auth;

/**
 * 登录结果
 *
 * @param token    JWT，后续请求放入 Authorization: Bearer {token}
 * @param role     角色：CUSTOMER / ADMIN，前端据此决定是否展示【店长工作台】
 * @param userInfo 用户信息
 */
public record LoginVO(String token, String role, UserInfoVO userInfo) {
}
