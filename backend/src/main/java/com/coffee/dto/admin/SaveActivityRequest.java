package com.coffee.dto.admin;

import java.time.LocalDateTime;

/**
 * 店长新增 / 编辑活动请求
 *
 * @param type         活动类型：SECKILL / FULL_REDUCTION
 * @param fullAmount   满减门槛（分），仅满减使用
 * @param reduceAmount 满减金额（分），仅满减使用
 */
public record SaveActivityRequest(
        String title,
        String type,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long fullAmount,
        Long reduceAmount,
        String remark
) {
}
