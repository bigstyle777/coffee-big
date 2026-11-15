package com.coffee.dto.order;

/**
 * 订单明细的规格快照，最终序列化后存入 coffee_order_item.spec_json
 *
 * @param groupId    规格组 ID
 * @param groupName  规格组名称快照
 * @param optionId   规格选项 ID
 * @param optionName 规格选项名称快照
 * @param extraPrice 该选项的加价（分）
 */
public record OrderItemSpecSnapshot(
        Long groupId,
        String groupName,
        Long optionId,
        String optionName,
        Long extraPrice
) {
}
