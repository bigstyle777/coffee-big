package com.coffee.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 取餐码生成器：Redis 按日自增，保证门店当日取餐码连续递增
 */
@Component
@RequiredArgsConstructor
public class PickupCodeGenerator {

    private static final String KEY_PREFIX = "coffee:pickup:code:";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    /** 取餐码前缀，当日序号超过 999 时自然扩展为 A1000 */
    private static final String CODE_PREFIX = "A";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成当日下一个取餐码，如 A001
     */
    public String nextPickupCode() {
        String key = KEY_PREFIX + LocalDate.now().format(DAY_FORMATTER);
        Long sequence = stringRedisTemplate.opsForValue().increment(key);
        if (sequence == null) {
            throw new IllegalStateException("取餐码生成失败：Redis INCR 返回空值");
        }
        if (sequence == 1L) {
            // 首次自增时设置过期时间，避免 key 无限堆积
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        }
        return CODE_PREFIX + String.format("%03d", sequence);
    }
}
