package com.coffee.vo.coupon;

import java.time.LocalDateTime;

/**
 * 可用优惠券
 *
 * @param discountAmount 优惠金额（分）
 * @param minAmount      使用门槛（分）
 */
public record CouponVO(
        Long id,
        String name,
        Long discountAmount,
        Long minAmount,
        LocalDateTime endTime
) {
}
