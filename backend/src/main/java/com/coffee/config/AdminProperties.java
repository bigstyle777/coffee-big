package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 店长后台配置
 *
 * @param defaultUsername  初始账号，仅在后台账号表为空时创建
 * @param defaultPassword  初始密码，仅在后台账号表为空时创建
 * @param tokenExpireHours admin-token 有效期（小时）
 */
@ConfigurationProperties(prefix = "coffee.admin")
public record AdminProperties(String defaultUsername, String defaultPassword, long tokenExpireHours) {
}
