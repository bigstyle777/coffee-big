package com.coffee.vo.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店长后台订单视图
 *
 * @param orderType             订单类型：NORMAL / SECKILL
 * @param activityDiscountAmount 活动优惠金额（分）
 * @param activityTitle         活动名称，可为空
 * @param pickupType            取餐方式
 * @param pickupCode            取餐码
 * @param totalAmount           商品总额（分）
 * @param discountAmount        优惠金额（分），包含活动优惠与优惠券
 * @param nickname              下单顾客昵称
 * @param phone                 下单顾客手机号
 * @param payTime               支付时间
 * @param cancelTime            取消时间
 */
public record AdminOrderVO(
        Long orderId,
        String orderNo,
        String orderType,
        String pickupCode,
        String pickupType,
        String status,
        Long totalAmount,
        Long discountAmount,
        Long activityDiscountAmount,
        Long payAmount,
        Integer itemCount,
        String remark,
        String nickname,
        String phone,
        String payChannel,
        String refundStatus,
        Long refundAmount,
        LocalDateTime refundTime,
        Long activityId,
        String activityTitle,
        LocalDateTime createTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        List<AdminOrderItemVO> items
) {
}
