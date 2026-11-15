package com.coffee.event;

/**
 * 订单退款请求事件
 * 订单取消事务提交后触发生效，避免外部退款调用拖长数据库事务
 *
 * @param orderId 订单 ID
 * @param reason  退款原因
 */
public record OrderRefundRequestedEvent(Long orderId, String reason) {
}
