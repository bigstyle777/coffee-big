package com.coffee.listener;

import com.coffee.event.OrderRefundRequestedEvent;
import com.coffee.service.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单退款监听器
 * 事务提交后再调用支付渠道退款，失败时订单保持退款中，交由后台人工处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRefundListener {

    private final PaymentFacade paymentFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRefund(OrderRefundRequestedEvent event) {
        try {
            paymentFacade.refund(event.orderId(), event.reason());
        } catch (Exception exception) {
            log.error("订单退款失败，订单保持退款中 orderId={}, reason={}",
                    event.orderId(), event.reason(), exception);
        }
    }
}
