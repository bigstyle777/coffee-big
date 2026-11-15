package com.coffee.dto.pay;

/**
 * 模拟支付确认请求
 *
 * @param orderId  订单 ID
 * @param prepayId 预支付 ID
 */
public record MockPayConfirmRequest(Long orderId, String prepayId) {
}
