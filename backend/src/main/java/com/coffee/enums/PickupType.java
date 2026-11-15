package com.coffee.enums;

/**
 * 取餐方式
 */
public enum PickupType {
    /** 到店自取 */
    SELF_PICKUP,
    /** 堂食 */
    DINE_IN;

    public static boolean isValid(String value) {
        for (PickupType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
