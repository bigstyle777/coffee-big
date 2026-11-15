package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.config.PaymentProperties;
import com.coffee.entity.CoffeeOrder;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.impl.MockPaymentService;
import com.coffee.service.impl.WechatPaymentService;
import com.coffee.vo.pay.PaymentPrepayVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付门面单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentFacadeTest {

    @Mock
    private MockPaymentService mockPaymentService;
    @Mock
    private WechatPaymentService wechatPaymentService;
    @Mock
    private CoffeeOrderMapper orderMapper;

    @Test
    @DisplayName("关闭模拟支付后拒绝发起支付")
    void prepayRejectsDisabledMock() {
        PaymentFacade facade = facade(false);

        assertThatThrownBy(() -> facade.prepay(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模拟支付已禁用");
    }

    @Test
    @DisplayName("开启模拟支付后返回 MOCK 渠道")
    void prepayReturnsMockProvider() {
        PaymentFacade facade = facade(true);
        when(mockPaymentService.prepay(1L, 2L)).thenReturn(PaymentPrepayVO.mock("prepay-test"));

        PaymentPrepayVO result = facade.prepay(1L, 2L);

        assertThat(result.provider()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("退款按订单实际支付渠道选择实现")
    void refundUsesOrderPayChannel() {
        PaymentFacade facade = facade(true);
        CoffeeOrder order = new CoffeeOrder();
        order.setId(3L);
        order.setPayChannel("WECHAT");
        when(orderMapper.selectById(3L)).thenReturn(order);

        facade.refund(3L, "顾客要求取消");

        verify(wechatPaymentService).refund(3L, "顾客要求取消");
    }

    private PaymentFacade facade(boolean mockEnabled) {
        PaymentProperties properties = new PaymentProperties(
                "mock", mockEnabled, null, null, null, null, null, null, null, null);
        return new PaymentFacade(properties, mockPaymentService, wechatPaymentService, orderMapper);
    }
}
