package com.coffee.controller;

import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.dto.pay.MockPayConfirmRequest;
import com.coffee.dto.pay.PaymentPrepayRequest;
import com.coffee.security.UserContext;
import com.coffee.service.PaymentFacade;
import com.coffee.vo.pay.PaymentPrepayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付接口
 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    /**
     * 发起支付：返回 MOCK 标识或微信小程序调起支付参数
     */
    @PostMapping("/prepay")
    public Result<PaymentPrepayVO> prepay(@RequestBody PaymentPrepayRequest request) {
        if (request == null || request.orderId() == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        return Result.ok(paymentFacade.prepay(UserContext.getUserId(), request.orderId()));
    }

    /**
     * 模拟支付确认
     * 前端用 prepay 返回的 prepayId 调用，后端生成唯一 transaction_id 并推进订单
     */
    @PostMapping("/mock/confirm")
    public Result<Void> confirmMockPay(@RequestBody MockPayConfirmRequest request) {
        if (request == null || request.orderId() == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        paymentFacade.confirmMockPay(UserContext.getUserId(), request.orderId(), request.prepayId());
        return Result.ok();
    }

    /**
     * 微信支付回调，公开接口，由微信服务器调用
     * 接入微信支付后需要按微信要求返回成功应答
     */
    @PostMapping("/notify/wechat")
    public Map<String, String> wechatNotify(@RequestBody String body,
                                            @RequestHeader Map<String, String> headers) {
        paymentFacade.handleWechatNotify(body, headers);
        return Map.of("code", "SUCCESS", "message", "成功");
    }
}
