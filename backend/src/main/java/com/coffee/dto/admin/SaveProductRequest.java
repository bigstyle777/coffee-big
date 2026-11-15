package com.coffee.dto.admin;

import java.util.List;

/**
 * 店长新增 / 编辑商品请求
 *
 * @param price        基础售价（分）
 * @param stock        库存（杯）
 * @param status       1-上架 / 0-下架
 * @param specGroupIds 绑定的规格组 ID，按数组顺序展示
 */
public record SaveProductRequest(
        Long categoryId,
        String name,
        String picUrl,
        Long price,
        Integer stock,
        Integer sort,
        Integer status,
        List<Long> specGroupIds
) {
}
