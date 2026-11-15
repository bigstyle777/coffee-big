package com.coffee.enums;

/**
 * 订单状态机：
 * PENDING_PAY -> MAKING -> READY -> COMPLETED
 * PENDING_PAY / MAKING 可流转至 CANCELLED
 */
public enum OrderStatus {
    /** 待支付 */
    PENDING_PAY,
    /** 制作中 */
    MAKING,
    /** 待取餐 */
    READY,
    /** 已完成 */
    COMPLETED,
    /** 已取消 */
    CANCELLED;

    /**
     * 判断状态值是否合法
     */
    public static boolean isValid(String value) {
        for (OrderStatus status : values()) {
            if (status.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
