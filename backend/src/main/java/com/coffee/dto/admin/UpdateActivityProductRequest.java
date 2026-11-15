package com.coffee.dto.admin;

/**
 * 修改活动商品配置请求
 */
public record UpdateActivityProductRequest(
        Long activityPrice,
        Integer activityStock,
        Integer limitPerUser
) {
}
