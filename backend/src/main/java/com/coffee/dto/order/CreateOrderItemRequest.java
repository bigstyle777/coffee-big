package com.coffee.dto.order;

import java.util.List;

/**
 * 创建订单的单条商品请求
 *
 * @param productId 商品 ID（仅作引用，价格以后端查库为准）
 * @param quantity  购买数量
 * @param optionIds 所选规格选项 ID 列表
 */
public record CreateOrderItemRequest(Long productId, Integer quantity, List<Long> optionIds) {
}
