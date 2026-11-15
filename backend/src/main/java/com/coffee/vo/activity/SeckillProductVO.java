package com.coffee.vo.activity;

import com.coffee.vo.menu.MenuSpecGroupVO;

import java.util.List;

/**
 * 顾客端秒杀商品
 *
 * @param activityProductId 活动商品绑定 ID，下单时传该 ID
 * @param originalPrice     商品原价（分）
 * @param activityPrice     秒杀基础价（分/杯），规格加价另计
 * @param remainingStock    剩余可售杯数，优先取 Redis
 * @param preheated         是否已完成 Redis 预热
 * @param specGroups        可选规格组
 */
public record SeckillProductVO(
        Long activityProductId,
        Long activityId,
        Long productId,
        String productName,
        String productPic,
        Long originalPrice,
        Long activityPrice,
        Integer activityStock,
        Integer soldCount,
        Integer remainingStock,
        Integer limitPerUser,
        boolean preheated,
        List<MenuSpecGroupVO> specGroups
) {
}
