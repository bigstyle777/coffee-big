package com.coffee.listener;

import com.coffee.event.SeckillStockRestoreEvent;
import com.coffee.service.SeckillStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 秒杀库存回滚监听器
 * 订单取消事务提交后再恢复 Redis，避免 DB 回滚导致库存被多加
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockRestoreListener {

    private static final Duration DEFAULT_TTL = Duration.ofDays(1);

    private final SeckillStockRepository seckillStockRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRestore(SeckillStockRestoreEvent event) {
        Duration ttl = resolveTtl(event.activityEndTime());
        event.quantityByBinding().forEach((bindingId, quantity) -> {
            try {
                seckillStockRepository.rollback(bindingId, event.userId(), quantity, ttl);
            } catch (Exception exception) {
                log.error("秒杀库存回滚失败，需人工对账 bindingId={}, userId={}, quantity={}",
                        bindingId, event.userId(), quantity, exception);
            }
        });
    }

    private Duration resolveTtl(LocalDateTime activityEndTime) {
        if (activityEndTime == null) {
            return DEFAULT_TTL;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), activityEndTime.plusDays(1));
        return ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }
}
