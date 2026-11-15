package com.coffee.vo.admin;

/**
 * 店长商品管理条目
 *
 * @param price  基础价格（分），不含规格加价
 * @param stock  当前库存（杯）
 * @param status 1-上架 / 0-下架
 */
public record AdminProductVO(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String picUrl,
        Long price,
        Integer stock,
        Integer status
) {
}
