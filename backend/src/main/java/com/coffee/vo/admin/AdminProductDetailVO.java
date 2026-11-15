package com.coffee.vo.admin;

import java.util.List;

/**
 * 商品编辑回显
 */
public record AdminProductDetailVO(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String picUrl,
        Long price,
        Integer stock,
        Integer sort,
        Integer status,
        List<Long> specGroupIds
) {
}
