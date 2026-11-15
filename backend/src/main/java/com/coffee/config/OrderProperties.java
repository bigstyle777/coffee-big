package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 订单配置
 *
 * @param payTimeoutMinutes 待支付超时时间（分钟）
 */
@ConfigurationProperties(prefix = "coffee.order")
public record OrderProperties(long payTimeoutMinutes) {
}
