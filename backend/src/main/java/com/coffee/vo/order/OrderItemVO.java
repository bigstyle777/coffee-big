package com.coffee.vo.order;

/**
 * 顾客端订单明细视图
 *
 * @param activityProductId 秒杀活动商品 ID，普通商品为空
 * @param specText          规格展示文案，如 大杯 / 热 / 半糖
 */
public record OrderItemVO(
        Long productId,
        Long activityProductId,
        String productName,
        String productPic,
        String specText,
        Integer quantity,
        Long unitPrice,
        Long subtotalAmount
) {
}
