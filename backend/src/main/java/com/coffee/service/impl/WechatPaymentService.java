package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.coffee.common.BusinessException;
import com.coffee.config.PaymentProperties;
import com.coffee.service.PaymentService;
import com.coffee.vo.pay.PaymentPrepayVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 微信支付 V3
 *
 * 当前项目没有商户号，本类保留了微信支付需要的配置与接口骨架，尚未发起真实请求。
 * 接入时按以下顺序补齐：
 *   1. 调用 /v3/pay/transactions/jsapi 获取 prepay_id
 *   2. 使用商户私钥签名小程序调起支付参数
 *   3. 回调验签、APIv3 密钥解密、金额校验与订单幂等更新
 *   4. 调用 /v3/refund/domestic/refunds 完成退款
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatPaymentService implements PaymentService {

    private static final String CHANNEL = "WECHAT";

    private final PaymentProperties paymentProperties;

    @Override
    public String provider() {
        return CHANNEL;
    }

    @Override
    public PaymentPrepayVO prepay(Long userId, Long orderId) {
        requireConfigured();
        throw new BusinessException("微信支付接口尚未接入，请先完成商户号与 JSAPI 下单配置");
    }

    /**
     * 微信支付回调
     */
    public void handleNotify(String body, Map<String, String> headers) {
        requireConfigured();
        throw new BusinessException("微信支付回调尚未接入");
    }

    @Override
    public void refund(Long orderId, String reason) {
        requireConfigured();
        throw new BusinessException("微信退款尚未接入");
    }

    /**
     * 检查微信支付配置是否完整，缺失时给出明确提示
     */
    private void requireConfigured() {
        List<String> missing = new ArrayList<>();
        if (StrUtil.isBlank(paymentProperties.wechatAppId())) {
            missing.add("wechatAppId");
        }
        if (StrUtil.isBlank(paymentProperties.wechatMchId())) {
            missing.add("wechatMchId");
        }
        if (StrUtil.isBlank(paymentProperties.wechatApiV3Key())) {
            missing.add("wechatApiV3Key");
        }
        if (StrUtil.isBlank(paymentProperties.wechatMerchantSerialNumber())) {
            missing.add("wechatMerchantSerialNumber");
        }
        if (StrUtil.isBlank(paymentProperties.wechatPrivateKeyPath())) {
            missing.add("wechatPrivateKeyPath");
        }
        if (StrUtil.isBlank(paymentProperties.notifyUrl())) {
            missing.add("notifyUrl");
        }
        if (!missing.isEmpty()) {
            log.warn("微信支付配置不完整，缺少：{}", String.join("、", missing));
            throw new BusinessException("微信支付未配置，缺少：" + String.join("、", missing));
        }
    }
}
