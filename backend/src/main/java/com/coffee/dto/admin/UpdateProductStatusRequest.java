package com.coffee.dto.admin;

/**
 * 店长商品上下架请求
 *
 * @param productId 商品 ID
 * @param status    状态：1-上架 / 0-下架
 */
public record UpdateProductStatusRequest(Long productId, Integer status) {
}
