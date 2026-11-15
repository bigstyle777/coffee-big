package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序配置
 *
 * @param appId             小程序 AppID
 * @param appSecret         小程序 AppSecret
 * @param jscode2sessionUrl 登录凭证校验接口地址
 */
@ConfigurationProperties(prefix = "coffee.wechat")
public record WechatProperties(String appId, String appSecret, String jscode2sessionUrl) {
}
