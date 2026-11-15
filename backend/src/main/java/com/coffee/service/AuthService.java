package com.coffee.service;

import com.coffee.dto.auth.WxLoginRequest;
import com.coffee.vo.auth.LoginVO;

/**
 * 认证业务
 */
public interface AuthService {

    /**
     * 微信小程序登录：openid 不存在则自动注册为顾客
     *
     * @param request 登录请求
     * @return Token 与用户信息
     */
    LoginVO wxLogin(WxLoginRequest request);

    /**
     * 体验登录：仅 dev 环境启用，用于本地未配置微信密钥时预览完整功能
     *
     * @return Token 与体验店长信息
     */
    LoginVO devLogin();
}
