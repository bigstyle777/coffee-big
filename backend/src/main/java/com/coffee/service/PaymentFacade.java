package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.config.PaymentProperties;
import com.coffee.entity.CoffeeOrder;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.impl.MockPaymentService;
import com.coffee.service.impl.WechatPaymentService;
import com.coffee.vo.pay.PaymentPrepayVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付门面：根据配置选择支付渠道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentProperties paymentProperties;
    private final MockPaymentService mockPaymentService;
    private final WechatPaymentService wechatPaymentService;
    private final CoffeeOrderMapper orderMapper;

    @PostConstruct
    void logProvider() {
        if (paymentProperties.useMockProvider()) {
            log.warn("当前支付渠道为 MOCK，模拟支付开关：{}。生产环境请关闭模拟支付并配置微信支付",
                    paymentProperties.mockEnabled() ? "开启" : "关闭");
        } else {
            log.info("当前支付渠道为 WECHAT，微信支付接口尚未接入");
        }
    }

    /**
     * 发起支付
     */
    public PaymentPrepayVO prepay(Long userId, Long orderId) {
        if (paymentProperties.useMockProvider()) {
            requireMockEnabled();
            return mockPaymentService.prepay(userId, orderId);
        }
        if (paymentProperties.useWechatProvider()) {
            return wechatPaymentService.prepay(userId, orderId);
        }
        throw new BusinessException("支付渠道配置错误，仅支持 mock / wechat");
    }

    /**
     * 模拟支付确认：校验 prepay_id 后走统一支付成功处理器
     */
    public void confirmMockPay(Long userId, Long orderId, String prepayId) {
        if (!paymentProperties.useMockProvider()) {
            throw new BusinessException("模拟支付已禁用");
        }
        requireMockEnabled();
        mockPaymentService.confirmPay(userId, orderId, prepayId);
    }

    /**
     * 微信支付回调
     */
    public void handleWechatNotify(String body, Map<String, String> headers) {
        if (!paymentProperties.useWechatProvider()) {
            throw new BusinessException("当前未启用微信支付");
        }
        wechatPaymentService.handleNotify(body, headers);
    }

    /**
     * 退款：按订单实际支付渠道选择实现，避免切换渠道后老订单退款走错通道
     */
    public void refund(Long orderId, String reason) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if ("WECHAT".equalsIgnoreCase(order.getPayChannel())) {
            wechatPaymentService.refund(orderId, reason);
            return;
        }
        mockPaymentService.refund(orderId, reason);
    }

    private void requireMockEnabled() {
        if (!paymentProperties.mockEnabled()) {
            throw new BusinessException("模拟支付已禁用，请配置微信支付后再发起支付");
        }
    }
}
