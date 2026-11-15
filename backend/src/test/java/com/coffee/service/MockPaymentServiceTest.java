package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.entity.CoffeeOrder;
import com.coffee.enums.OrderStatus;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.impl.MockPaymentService;
import com.coffee.vo.pay.PaymentPrepayVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟支付单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockPaymentServiceTest {

    @Mock
    private CoffeeOrderMapper orderMapper;
    @Mock
    private OrderService orderService;

    private MockPaymentService mockPaymentService;

    @BeforeEach
    void setUp() {
        mockPaymentService = new MockPaymentService(orderMapper, orderService);
        when(orderMapper.update(any(CoffeeOrder.class), any())).thenReturn(1);
    }

    @Test
    @DisplayName("预支付生成唯一 prepay_id 并落库")
    void prepayGeneratesAndStoresPrepayId() {
        CoffeeOrder order = pendingOrder(null);
        when(orderMapper.selectById(10L)).thenReturn(order);

        PaymentPrepayVO result = mockPaymentService.prepay(1L, 10L);

        assertThat(result.provider()).isEqualTo("MOCK");
        assertThat(result.prepayId()).startsWith("mock_prepay_");
        ArgumentCaptor<CoffeeOrder> captor = ArgumentCaptor.forClass(CoffeeOrder.class);
        verify(orderMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getPrepayId()).isEqualTo(result.prepayId());
        assertThat(captor.getValue().getPayChannel()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("同一订单重复预支付复用已有 prepay_id")
    void prepayIsIdempotent() {
        CoffeeOrder order = pendingOrder("prepay-existing");
        when(orderMapper.selectById(10L)).thenReturn(order);

        PaymentPrepayVO result = mockPaymentService.prepay(1L, 10L);

        assertThat(result.prepayId()).isEqualTo("prepay-existing");
        verify(orderMapper, never()).update(any(CoffeeOrder.class), any());
    }

    @Test
    @DisplayName("支付确认校验 prepay_id 并走统一支付成功处理器")
    void confirmPayUsesSharedPaymentSuccess() {
        CoffeeOrder order = pendingOrder("prepay-1");
        when(orderMapper.selectById(10L)).thenReturn(order);

        mockPaymentService.confirmPay(1L, 10L, "prepay-1");

        ArgumentCaptor<String> transactionCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderService).confirmPaymentSuccess(
                eq(10L), eq("MOCK"), transactionCaptor.capture(), eq(1800L));
        assertThat(transactionCaptor.getValue()).startsWith("mock_txn_");
    }

    @Test
    @DisplayName("prepay_id 不匹配时拒绝确认支付")
    void confirmRejectsWrongPrepayId() {
        CoffeeOrder order = pendingOrder("prepay-1");
        when(orderMapper.selectById(10L)).thenReturn(order);

        assertThatThrownBy(() -> mockPaymentService.confirmPay(1L, 10L, "prepay-wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预支付标识不匹配");
        verify(orderService, never()).confirmPaymentSuccess(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("已支付订单重复确认直接返回")
    void confirmIsIdempotentForPaidOrder() {
        CoffeeOrder order = pendingOrder("prepay-1");
        order.setStatus(OrderStatus.MAKING.name());
        when(orderMapper.selectById(10L)).thenReturn(order);

        mockPaymentService.confirmPay(1L, 10L, "prepay-1");

        verify(orderService, never()).confirmPaymentSuccess(any(), any(), any(), anyLong());
    }

    private CoffeeOrder pendingOrder(String prepayId) {
        CoffeeOrder order = new CoffeeOrder();
        order.setId(10L);
        order.setUserId(1L);
        order.setOrderNo("ORDER-10");
        order.setStatus(OrderStatus.PENDING_PAY.name());
        order.setPayAmount(1800L);
        order.setPrepayId(prepayId);
        order.setPayChannel("MOCK");
        return order;
    }
}
