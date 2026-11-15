package com.coffee.dto.order;

import java.util.List;

/**
 * 秒杀下单请求
 *
 * @param activityProductId 活动商品绑定 ID
 * @param quantity          购买数量
 * @param optionIds         规格选项 ID 列表
 * @param pickupType        取餐方式
 * @param remark            顾客备注
 * @param requestId         客户端幂等键，秒杀场景必填
 */
public record CreateSeckillOrderRequest(
        Long activityProductId,
        Integer quantity,
        List<Long> optionIds,
        String pickupType,
        String remark,
        String requestId
) {
}
