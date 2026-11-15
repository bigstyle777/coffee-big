package com.coffee.vo.admin;

import java.time.LocalDateTime;

/**
 * 后台活动列表条目
 *
 * @param status          实时状态：NOT_STARTED / RUNNING / ENDED / CLOSED
 * @param productCount    当前生效的绑定商品数
 * @param totalStock      当前生效绑定商品的限量合计
 * @param soldCount       已售合计（包含已停用绑定的历史销量）
 * @param preheatRequired 是否需要 Redis 预热（秒杀活动为 true）
 */
public record AdminActivityVO(
        Long id,
        String title,
        String type,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer isOpen,
        String status,
        Long fullAmount,
        Long reduceAmount,
        String remark,
        int productCount,
        int totalStock,
        int soldCount,
        boolean preheatRequired,
        LocalDateTime createTime
) {
}
