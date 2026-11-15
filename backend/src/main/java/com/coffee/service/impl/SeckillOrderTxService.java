package com.coffee.service.impl;

import com.coffee.common.BusinessException;
import com.coffee.dto.order.SeckillOrderContext;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeActivityProduct;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.enums.ActivityType;
import com.coffee.enums.OrderStatus;
import com.coffee.enums.UserRole;
import com.coffee.event.OrderStatusChangedEvent;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.vo.order.OrderCreateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 秒杀订单落库事务
 * 与 Redis 编排分离，确保 DB 事务只包含数据库操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderTxService {

    private static final int BINDING_ENABLED = 1;
    private static final int ACTIVITY_OPEN = 1;
    private static final DateTimeFormatter ORDER_NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CoffeeActivityProductMapper activityProductMapper;
    private final CoffeeActivityMapper activityMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeOrderMapper orderMapper;
    private final CoffeeOrderItemMapper orderItemMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createSeckillOrder(SeckillOrderContext context) {
        CoffeeActivityProduct binding = activityProductMapper.selectById(context.activityProductId());
        if (binding == null || binding.getStatus() == null || binding.getStatus() != BINDING_ENABLED) {
            throw new BusinessException("活动商品已停用");
        }
        CoffeeActivity activity = activityMapper.selectById(binding.getActivityId());
        if (activity == null || !ActivityType.SECKILL.name().equals(activity.getType())) {
            throw new BusinessException("秒杀活动不存在");
        }
        if (activity.getIsOpen() == null || activity.getIsOpen() != ACTIVITY_OPEN) {
            throw new BusinessException("活动已关闭");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException("活动尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException("活动已结束");
        }
        if (!Objects.equals(binding.getActivityPrice(), context.activityPrice())) {
            throw new BusinessException("活动价已变化，请重新下单");
        }

        long expectedTotal = context.unitPrice() * context.quantity();
        if (expectedTotal != context.totalAmount()) {
            throw new BusinessException("订单金额异常，请重新下单");
        }

        int stockRows = productMapper.deductStock(context.productId(), context.quantity());
        if (stockRows == 0) {
            throw new BusinessException("商品库存不足，请稍后重试");
        }
        int soldRows = activityProductMapper.incrementSoldCount(
                context.activityProductId(), context.quantity());
        if (soldRows == 0) {
            throw new BusinessException("活动库存不足，请稍后重试");
        }

        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(context.userId());
        order.setStatus(OrderStatus.PENDING_PAY.name());
        order.setOrderType("SECKILL");
        order.setPickupType(context.pickupType());
        order.setTotalAmount(context.totalAmount());
        order.setDiscountAmount(0L);
        order.setActivityDiscountAmount(0L);
        order.setActivityId(activity.getId());
        order.setPayAmount(context.totalAmount());
        order.setItemCount(context.quantity());
        order.setRemark(context.remark());
        order.setRequestId(context.requestId());
        orderMapper.insert(order);

        CoffeeOrderItem item = new CoffeeOrderItem();
        item.setOrderId(order.getId());
        item.setProductId(context.productId());
        item.setActivityProductId(context.activityProductId());
        item.setProductName(context.productName());
        item.setProductPic(context.productPic());
        item.setBasePrice(context.basePrice());
        item.setSpecExtraPrice(context.specExtraPrice());
        item.setSpecText(context.specText());
        item.setUnitPrice(context.unitPrice());
        item.setQuantity(context.quantity());
        item.setSubtotalAmount(context.totalAmount());
        item.setSpecJson(context.specJson());
        orderItemMapper.insert(item);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getId(), null, OrderStatus.PENDING_PAY.name(),
                context.userId(), UserRole.CUSTOMER.name(), "创建秒杀订单"));

        log.info("秒杀订单创建成功 orderId={}, orderNo={}, userId={}, activityProductId={}, quantity={}, total={}分",
                order.getId(), order.getOrderNo(), context.userId(),
                context.activityProductId(), context.quantity(), context.totalAmount());
        return new OrderCreateVO(
                order.getId(),
                order.getOrderNo(),
                null,
                order.getPickupType(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayAmount(),
                order.getItemCount(),
                order.getStatus());
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
