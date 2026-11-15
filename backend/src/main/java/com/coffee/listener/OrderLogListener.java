package com.coffee.listener;

import com.coffee.entity.CoffeeOrderLog;
import com.coffee.event.OrderStatusChangedEvent;
import com.coffee.mapper.CoffeeOrderLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单状态流水监听器
 * AFTER_COMMIT + @Async：主事务提交后才在虚拟线程中写流水，主事务回滚则不会产生脏数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLogListener {

    private final CoffeeOrderLogMapper orderLogMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        CoffeeOrderLog orderLog = new CoffeeOrderLog();
        orderLog.setOrderId(event.orderId());
        orderLog.setFromStatus(event.fromStatus());
        orderLog.setToStatus(event.toStatus());
        orderLog.setOperatorId(event.operatorId());
        orderLog.setOperatorRole(event.operatorRole());
        orderLog.setRemark(event.remark());
        try {
            orderLogMapper.insert(orderLog);
        } catch (Exception exception) {
            // 流水写入失败不能影响已经完成的订单，记录日志后交由补偿/告警处理
            log.error("写入订单状态流水失败，orderId={}, toStatus={}", event.orderId(), event.toStatus(), exception);
        }
    }
}
