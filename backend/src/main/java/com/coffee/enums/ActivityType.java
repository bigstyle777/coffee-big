package com.coffee.enums;

/**
 * 活动类型
 */
public enum ActivityType {
    /** 秒杀：固定活动价、限量、Redis 预热 */
    SECKILL,
    /** 满减：满足门槛后减固定金额 */
    FULL_REDUCTION;

    public static boolean isValid(String value) {
        for (ActivityType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
