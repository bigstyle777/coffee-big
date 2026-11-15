package com.coffee.event;

/**
 * 订单状态变更事件
 * 主事务提交后由监听器异步写入 coffee_order_log，避免回滚产生脏流水
 *
 * @param orderId      订单 ID
 * @param fromStatus   变更前状态，创建订单时为 null
 * @param toStatus     变更后状态
 * @param operatorId   操作人用户 ID，系统自动流转时为 null
 * @param operatorRole 操作人角色
 * @param remark       备注
 */
public record OrderStatusChangedEvent(
        Long orderId,
        String fromStatus,
        String toStatus,
        Long operatorId,
        String operatorRole,
        String remark
) {
}
