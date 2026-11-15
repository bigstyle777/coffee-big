package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.entity.CoffeeOrder;
import com.coffee.enums.OrderStatus;
import com.coffee.event.OrderStatusChangedEvent;
import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.SysUserMapper;
import com.coffee.service.impl.AdminOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店长订单后台单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminOrderServiceImplTest {

    @Mock
    private CoffeeOrderMapper orderMapper;
    @Mock
    private CoffeeOrderItemMapper orderItemMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private com.coffee.mapper.CoffeeActivityMapper activityMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminOrderServiceImpl adminOrderService;

    @BeforeEach
    void setUp() {
        adminOrderService = new AdminOrderServiceImpl(
                orderMapper, orderItemMapper, userMapper, activityMapper,
                orderService, eventPublisher);
    }

    @Test
    @DisplayName("已支付订单取消：更新为已取消并回滚库存与优惠券")
    void cancelPaidOrderRestoresResources() {
        CoffeeOrder order = new CoffeeOrder();
        order.setId(10L);
        order.setUserId(9L);
        order.setStatus(OrderStatus.MAKING.name());
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.update(any(CoffeeOrder.class), any())).thenReturn(1);

        adminOrderService.cancelOrder(1L, 10L, "顾客要求取消");

        ArgumentCaptor<CoffeeOrder> updateCaptor = ArgumentCaptor.forClass(CoffeeOrder.class);
        verify(orderMapper).update(updateCaptor.capture(), any());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(updateCaptor.getValue().getCancelTime()).isNotNull();
        verify(orderService).restoreResourcesForCancelledOrder(10L);
        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("重复取消：幂等返回，不重复回滚")
    void cancelCancelledOrderIsIdempotent() {
        CoffeeOrder order = new CoffeeOrder();
        order.setId(10L);
        order.setStatus(OrderStatus.CANCELLED.name());
        when(orderMapper.selectById(10L)).thenReturn(order);

        adminOrderService.cancelOrder(1L, 10L, null);

        verify(orderMapper, never()).update(any(CoffeeOrder.class), any());
        verify(orderService, never()).restoreResourcesForCancelledOrder(10L);
    }

    @Test
    @DisplayName("已完成订单不能取消")
    void cancelCompletedOrderIsRejected() {
        CoffeeOrder order = new CoffeeOrder();
        order.setId(10L);
        order.setStatus(OrderStatus.COMPLETED.name());
        when(orderMapper.selectById(10L)).thenReturn(order);

        assertThatThrownBy(() -> adminOrderService.cancelOrder(1L, 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成订单不能取消");
        verify(orderMapper, never()).update(any(CoffeeOrder.class), any());
    }

    @Test
    @DisplayName("取消原因过长时拒绝请求")
    void cancelRejectsTooLongReason() {
        String reason = "a".repeat(201);

        assertThatThrownBy(() -> adminOrderService.cancelOrder(1L, 10L, reason))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取消原因不能超过");
        verify(orderMapper, never()).selectById(eq(10L));
    }
}
