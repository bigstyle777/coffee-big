package com.coffee.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 秒杀库存回滚事件
 * 订单取消事务提交后，再由监听器恢复 Redis 活动库存与用户限购
 *
 * @param userId              下单用户 ID
 * @param quantityByBinding   活动商品绑定 ID -> 回滚数量
 * @param activityEndTime     活动结束时间，用于计算 Redis TTL，可为空
 */
public record SeckillStockRestoreEvent(
        Long userId,
        Map<Long, Integer> quantityByBinding,
        LocalDateTime activityEndTime
) {
}
