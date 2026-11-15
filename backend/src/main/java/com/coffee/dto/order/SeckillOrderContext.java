package com.coffee.dto.order;

/**
 * 秒杀订单落库上下文：所有金额与规格快照已在编排阶段计算完成
 */
public record SeckillOrderContext(
        Long userId,
        Long activityId,
        Long activityProductId,
        Long productId,
        String productName,
        String productPic,
        Long basePrice,
        Long activityPrice,
        Long specExtraPrice,
        String specText,
        String specJson,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String pickupType,
        String remark,
        String requestId
) {
}
