package com.coffee.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.config.OrderProperties;
import com.coffee.entity.CoffeeOrder;
import com.coffee.enums.OrderStatus;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待支付订单超时关单任务
 * 每分钟扫描一次，关闭超过配置时间仍未支付的订单，并回滚库存与优惠券
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 单批处理上限，避免一次扫描过多订单 */
    private static final int BATCH_SIZE = 100;

    private final CoffeeOrderMapper orderMapper;
    private final OrderService orderService;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelExpiredOrders() {
        long timeoutMinutes = orderProperties.payTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<CoffeeOrder> expiredOrders = orderMapper.selectList(
                Wrappers.<CoffeeOrder>lambdaQuery()
                        .eq(CoffeeOrder::getStatus, OrderStatus.PENDING_PAY.name())
                        .lt(CoffeeOrder::getCreateTime, cutoff)
                        .orderByAsc(CoffeeOrder::getCreateTime)
                        .last("LIMIT " + BATCH_SIZE));
        if (expiredOrders.isEmpty()) {
            return;
        }
        for (CoffeeOrder order : expiredOrders) {
            try {
                orderService.cancelExpiredOrder(order.getId(), cutoff);
            } catch (Exception exception) {
                log.error("超时关单失败 orderId={}", order.getId(), exception);
            }
        }
    }
}
