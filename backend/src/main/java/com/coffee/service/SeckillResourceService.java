package com.coffee.service;

import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.event.SeckillStockRestoreEvent;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀订单资源恢复
 * DB 侧 sold_count 在当前事务内回滚，Redis 侧在事务提交后通过事件恢复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillResourceService {

    private final CoffeeActivityProductMapper activityProductMapper;
    private final CoffeeActivityMapper activityMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 取消订单时恢复秒杀活动销量与 Redis 库存
     */
    public void restoreForCancelledOrder(CoffeeOrder order, List<CoffeeOrderItem> items) {
        Map<Long, Integer> quantityByBinding = new LinkedHashMap<>();
        for (CoffeeOrderItem item : items) {
            if (item.getActivityProductId() != null && item.getQuantity() != null) {
                quantityByBinding.merge(item.getActivityProductId(), item.getQuantity(), Integer::sum);
            }
        }
        if (quantityByBinding.isEmpty()) {
            return;
        }

        quantityByBinding.forEach((bindingId, quantity) -> {
            int updatedRows = activityProductMapper.decreaseSoldCount(bindingId, quantity);
            if (updatedRows == 0) {
                log.warn("回滚活动销量失败，可能已回滚或数据不一致 bindingId={}, quantity={}",
                        bindingId, quantity);
            }
        });

        CoffeeActivity activity = order.getActivityId() == null
                ? null : activityMapper.selectById(order.getActivityId());
        eventPublisher.publishEvent(new SeckillStockRestoreEvent(
                order.getUserId(),
                Map.copyOf(quantityByBinding),
                activity == null ? null : activity.getEndTime()));
        log.info("秒杀订单资源已回滚 orderId={}, bindings={}", order.getId(), quantityByBinding);
    }
}
