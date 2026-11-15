package com.coffee.vo.admin;

/**
 * 店长后台登录结果
 *
 * @param token admin-token，后续请求放入 Authorization: Bearer {token}
 * @param admin 当前登录店长信息
 */
public record AdminLoginVO(String token, AdminInfoVO admin) {
}
