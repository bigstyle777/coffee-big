package com.coffee.vo.admin;

import lombok.Data;

/**
 * 热销饮品 SQL 查询行，由 MyBatis 按列名映射
 */
@Data
public class HotProductRow {

    private String productName;

    private Long totalQuantity;
}
