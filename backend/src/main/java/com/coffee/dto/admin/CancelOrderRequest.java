package com.coffee.dto.admin;

/**
 * 店长取消订单请求
 *
 * @param reason 取消原因，会写入订单流水，退款场景先记录原因
 */
public record CancelOrderRequest(String reason) {
}
