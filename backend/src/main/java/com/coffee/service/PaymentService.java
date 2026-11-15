package com.coffee.service;

import com.coffee.vo.pay.PaymentPrepayVO;

/**
 * 支付渠道抽象
 * 新增支付方式时实现该接口，并在 PaymentFacade 中选择
 */
public interface PaymentService {

    /**
     * 渠道标识：MOCK / WECHAT
     */
    String provider();

    /**
     * 发起支付，返回前端调起支付所需参数
     */
    PaymentPrepayVO prepay(Long userId, Long orderId);

    /**
     * 退款
     */
    void refund(Long orderId, String reason);
}
