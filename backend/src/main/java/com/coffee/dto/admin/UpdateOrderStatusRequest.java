package com.coffee.dto.admin;

/**
 * 店长更新订单状态请求
 *
 * @param orderId 订单 ID
 * @param status  目标状态：READY（制作完成）/ COMPLETED（已取餐）
 */
public record UpdateOrderStatusRequest(Long orderId, String status) {
}
