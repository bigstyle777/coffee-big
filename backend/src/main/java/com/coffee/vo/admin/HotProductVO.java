package com.coffee.vo.admin;

/**
 * 热销饮品统计
 *
 * @param productName   商品名称
 * @param totalQuantity 售出杯数
 */
public record HotProductVO(String productName, Long totalQuantity) {
}
