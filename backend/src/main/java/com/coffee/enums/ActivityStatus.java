package com.coffee.enums;

import java.time.LocalDateTime;

/**
 * 活动实时状态，由开关与起止时间推导，不落库
 */
public enum ActivityStatus {
    /** 未开始 */
    NOT_STARTED,
    /** 进行中 */
    RUNNING,
    /** 已结束 */
    ENDED,
    /** 已关闭 */
    CLOSED;

    public static ActivityStatus resolve(Integer isOpen, LocalDateTime startTime,
                                         LocalDateTime endTime, LocalDateTime now) {
        if (isOpen == null || isOpen != 1) {
            return CLOSED;
        }
        if (now.isBefore(startTime)) {
            return NOT_STARTED;
        }
        if (now.isAfter(endTime)) {
            return ENDED;
        }
        return RUNNING;
    }

    public static boolean isValid(String value) {
        for (ActivityStatus status : values()) {
            if (status.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
