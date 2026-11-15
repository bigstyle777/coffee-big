package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置
 *
 * @param jwtSecret       JWT 签名密钥，长度不少于 32 位
 * @param jwtExpireHours  Token 有效期（小时）
 * @param adminOpenid     初始店长微信 openid，首次登录后自动提升为 ADMIN
 * @param devLoginEnabled 是否开启体验登录（仅 dev 环境使用）
 */
@ConfigurationProperties(prefix = "coffee.auth")
public record AuthProperties(String jwtSecret, long jwtExpireHours, String adminOpenid, boolean devLoginEnabled) {
}
