package com.coffee.vo.order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾客端订单视图，列表与详情共用
 *
 * @param orderType             订单类型：NORMAL / SECKILL
 * @param activityDiscountAmount 活动优惠金额（分）
 * @param activityTitle         活动名称，可为空
 */
public record OrderVO(
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
        Long activityId,
        String activityTitle,
        LocalDateTime createTime,
        LocalDateTime payTime,
        List<OrderItemVO> items
) {
}
