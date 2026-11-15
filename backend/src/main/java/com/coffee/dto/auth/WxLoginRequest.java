package com.coffee.dto.auth;

/**
 * 微信登录请求
 *
 * @param code 小程序 wx.login 获取的临时登录凭证
 */
public record WxLoginRequest(String code) {
}
