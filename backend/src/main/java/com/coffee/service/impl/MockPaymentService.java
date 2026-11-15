package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.entity.CoffeeOrder;
import com.coffee.enums.OrderStatus;
import com.coffee.enums.RefundStatus;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.OrderService;
import com.coffee.service.PaymentService;
import com.coffee.vo.pay.PaymentPrepayVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 模拟支付
 * 尽量复刻微信支付流程：
 *   1. prepay 生成唯一 prepay_id 并落库
 *   2. 前端确认支付时校验 prepay_id
 *   3. 生成唯一 transaction_id，通过统一支付成功处理器推进订单
 *   4. 退款写入退款状态与退款单号
 * 仅用于本地开发与演示，生产环境必须通过 coffee.pay.mock-enabled=false 关闭
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentService implements PaymentService {

    private static final String CHANNEL = "MOCK";
    private static final String PREPAY_ID_PREFIX = "mock_prepay_";
    private static final String TRANSACTION_ID_PREFIX = "mock_txn_";

    private final CoffeeOrderMapper orderMapper;
    private final OrderService orderService;

    @Override
    public String provider() {
        return CHANNEL;
    }

    @Override
    public PaymentPrepayVO prepay(Long userId, Long orderId) {
        CoffeeOrder order = getExistingOrder(orderId);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权操作该订单");
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            throw new BusinessException("订单已取消，无法支付");
        }
        if (StrUtil.isNotBlank(order.getPayChannel())
                && !CHANNEL.equalsIgnoreCase(order.getPayChannel())) {
            throw new BusinessException("该订单已使用其他支付渠道");
        }
        if (!OrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw new BusinessException("订单已支付，请勿重复操作");
        }
        // 幂等：同一订单重复发起支付时复用已有 prepay_id
        if (StrUtil.isNotBlank(order.getPrepayId())) {
            return PaymentPrepayVO.mock(order.getPrepayId());
        }

        String prepayId = PREPAY_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setPayChannel(CHANNEL);
        updateOrder.setPrepayId(prepayId);
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .eq(CoffeeOrder::getStatus, OrderStatus.PENDING_PAY.name()));
        if (updatedRows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        log.info("模拟预支付创建成功 orderId={}, prepayId={}", orderId, prepayId);
        return PaymentPrepayVO.mock(prepayId);
    }

    /**
     * 模拟用户在支付面板完成付款
     * 校验 prepay_id 后生成唯一交易号，并走统一支付成功处理器
     */
    public void confirmPay(Long userId, Long orderId, String prepayId) {
        CoffeeOrder order = getExistingOrder(orderId);
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权操作该订单");
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            throw new BusinessException("订单已取消，无法支付");
        }
        if (StrUtil.isNotBlank(order.getPayChannel())
                && !CHANNEL.equalsIgnoreCase(order.getPayChannel())) {
            throw new BusinessException("该订单已使用其他支付渠道");
        }
        // 幂等：已支付订单直接返回
        if (OrderStatus.MAKING.name().equals(order.getStatus())
                || OrderStatus.READY.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw new BusinessException("当前订单状态不允许支付");
        }
        if (StrUtil.isBlank(order.getPrepayId())) {
            throw new BusinessException("请先发起支付");
        }
        if (!order.getPrepayId().equals(prepayId)) {
            throw new BusinessException("预支付标识不匹配，请重新发起支付");
        }

        String transactionId = TRANSACTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
        orderService.confirmPaymentSuccess(orderId, CHANNEL, transactionId, order.getPayAmount());
        log.info("模拟支付确认成功 orderId={}, prepayId={}, transactionId={}",
                orderId, prepayId, transactionId);
    }

    @Override
    public void refund(Long orderId, String reason) {
        CoffeeOrder order = getExistingOrder(orderId);
        if (order.getPayChannel() != null && !CHANNEL.equalsIgnoreCase(order.getPayChannel())) {
            throw new BusinessException("该订单不是模拟支付订单，无法使用模拟退款");
        }
        if (RefundStatus.REFUNDED.name().equals(order.getRefundStatus())) {
            return;
        }

        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setRefundStatus(RefundStatus.REFUNDED.name());
        updateOrder.setRefundNo("mock_refund_" + order.getOrderNo());
        updateOrder.setRefundAmount(order.getPayAmount());
        updateOrder.setRefundTime(LocalDateTime.now());
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .in(CoffeeOrder::getRefundStatus,
                        RefundStatus.NONE.name(), RefundStatus.REFUNDING.name()));
        if (updatedRows == 0) {
            log.warn("模拟退款状态更新未生效 orderId={}, currentRefundStatus={}",
                    orderId, order.getRefundStatus());
            return;
        }
        log.info("模拟退款成功 orderId={}, refundAmount={}分, reason={}",
                orderId, order.getPayAmount(), reason);
    }

    private CoffeeOrder getExistingOrder(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }
}
