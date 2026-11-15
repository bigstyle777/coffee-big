package com.coffee.enums;

/**
 * 退款状态
 */
public enum RefundStatus {
    /** 未退款 */
    NONE,
    /** 退款中 */
    REFUNDING,
    /** 已退款 */
    REFUNDED,
    /** 退款失败 */
    FAILED
}
