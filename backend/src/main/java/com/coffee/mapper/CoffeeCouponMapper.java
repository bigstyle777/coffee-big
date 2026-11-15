package com.coffee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coffee.entity.CoffeeCoupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券 Mapper
 */
public interface CoffeeCouponMapper extends BaseMapper<CoffeeCoupon> {

    /**
     * 原子核销优惠券，避免并发下单重复使用同一张券
     */
    @Update("""
            UPDATE coffee_coupon
            SET status = 'USED',
                used_order_id = #{orderId},
                used_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{couponId}
              AND user_id = #{userId}
              AND status = 'UNUSED'
              AND deleted = 0
            """)
    int markUsed(@Param("couponId") Long couponId,
                 @Param("userId") Long userId,
                 @Param("orderId") Long orderId);

    /**
     * 取消订单 / 超时关单时退回优惠券
     */
    @Update("""
            UPDATE coffee_coupon
            SET status = 'UNUSED',
                used_order_id = NULL,
                used_time = NULL
            WHERE id = #{couponId}
              AND used_order_id = #{orderId}
              AND status = 'USED'
              AND deleted = 0
            """)
    int restore(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
}
