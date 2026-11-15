package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付配置
 *
 * @param provider                  支付渠道：mock / wechat
 * @param mockEnabled               是否允许模拟支付，生产环境必须为 false
 * @param notifyUrl                 微信支付回调地址
 * @param wechatAppId               微信小程序 AppID
 * @param wechatMchId               微信支付商户号
 * @param wechatApiV3Key            微信支付 APIv3 密钥
 * @param wechatMerchantSerialNumber 商户 API 证书序列号
 * @param wechatPrivateKeyPath      商户私钥路径
 * @param wechatPublicKeyId         微信支付公钥 ID
 * @param wechatPublicKeyPath       微信支付公钥路径
 */
@ConfigurationProperties(prefix = "coffee.pay")
public record PaymentProperties(
        String provider,
        boolean mockEnabled,
        String notifyUrl,
        String wechatAppId,
        String wechatMchId,
        String wechatApiV3Key,
        String wechatMerchantSerialNumber,
        String wechatPrivateKeyPath,
        String wechatPublicKeyId,
        String wechatPublicKeyPath
) {

    public boolean useMockProvider() {
        return provider == null || "mock".equalsIgnoreCase(provider);
    }

    public boolean useWechatProvider() {
        return "wechat".equalsIgnoreCase(provider);
    }
}
