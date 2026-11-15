package com.coffee.vo.order;

/**
 * 创建订单返回结果，金额单位均为分
 *
 * @param orderId    订单 ID
 * @param orderNo    业务订单号
 * @param pickupCode 取餐码，支付成功后生成
 * @param pickupType 取餐方式
 * @param totalAmount 商品总额
 * @param discountAmount 优惠金额
 * @param payAmount  实付金额
 * @param itemCount  商品总件数
 * @param status     订单状态
 */
public record OrderCreateVO(
        Long orderId,
        String orderNo,
        String pickupCode,
        String pickupType,
        Long totalAmount,
        Long discountAmount,
        Long payAmount,
        Integer itemCount,
        String status
) {
}
