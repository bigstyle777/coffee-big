package com.coffee.service;

import com.coffee.vo.coupon.CouponVO;

import java.util.List;

/**
 * 优惠券业务
 */
public interface CouponService {

    /**
     * 查询当前用户在指定订单金额下可用的优惠券
     *
     * @param userId 当前用户 ID
     * @param amount 商品总额（分）
     */
    List<CouponVO> listAvailable(Long userId, Long amount);

    /**
     * 校验优惠券并返回可抵扣金额，couponId 为空时返回 0
     */
    long calculateDiscount(Long userId, Long couponId, Long totalAmount);

    /**
     * 核销优惠券，失败说明已被其他订单占用
     */
    void markUsed(Long userId, Long couponId, Long orderId);

    /**
     * 取消订单时退回优惠券
     */
    void restore(Long couponId, Long orderId);

    /**
     * 新用户首次登录发放一张新客券；已发过则跳过
     */
    void grantWelcomeCouponIfNeeded(Long userId);
}
