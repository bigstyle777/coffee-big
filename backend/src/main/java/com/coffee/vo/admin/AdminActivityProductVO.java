package com.coffee.vo.admin;

/**
 * 后台活动商品条目
 *
 * @param productPrice  商品当前原价（分）
 * @param productStatus 商品状态：1-上架 / 0-下架
 * @param redisStock    Redis 剩余活动库存，未预热时为 null
 * @param preheated     是否已完成 Redis 预热
 */
public record AdminActivityProductVO(
        Long bindingId,
        Long productId,
        String productName,
        String productPic,
        Long productPrice,
        Integer productStatus,
        Long activityPrice,
        Integer activityStock,
        Integer soldCount,
        Integer limitPerUser,
        Integer status,
        Integer sort,
        Integer redisStock,
        boolean preheated
) {
}
