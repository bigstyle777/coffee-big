package com.coffee.vo.admin;

/**
 * 店长工作台订单明细视图
 *
 * @param specText 规格展示文案，如 大杯 / 热 / 半糖
 */
public record AdminOrderItemVO(
        Long productId,
        String productName,
        String specText,
        Integer quantity,
        Long unitPrice,
        Long subtotalAmount
) {
}
