package com.coffee.dto.order;

import java.util.List;

/**
 * 创建订单请求
 *
 * @param items      商品明细，只传商品 ID、数量、规格选项 ID，金额由后端重算
 * @param pickupType 取餐方式：SELF_PICKUP-到店自取 / DINE_IN-堂食
 * @param remark     顾客备注
 * @param couponId   使用的优惠券 ID，可为空
 * @param requestId  客户端幂等键，同一笔提交重试时保持不变
 */
public record CreateOrderRequest(
        List<CreateOrderItemRequest> items,
        String pickupType,
        String remark,
        Long couponId,
        String requestId
) {
}
