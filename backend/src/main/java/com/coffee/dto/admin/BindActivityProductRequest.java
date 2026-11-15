package com.coffee.dto.admin;

/**
 * 活动绑定商品请求
 *
 * @param activityPrice 秒杀价（分/杯），仅秒杀使用
 * @param activityStock 活动限量（杯），仅秒杀使用
 * @param limitPerUser  每人限购（杯），仅秒杀使用
 */
public record BindActivityProductRequest(
        Long productId,
        Long activityPrice,
        Integer activityStock,
        Integer limitPerUser
) {
}
