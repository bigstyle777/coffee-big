package com.coffee.vo.pay;

/**
 * 预支付结果
 *
 * @param provider     支付渠道：MOCK / WECHAT
 * @param prepayId     预支付 ID，Mock 渠道也会返回唯一值
 * @param timeStamp    微信支付时间戳
 * @param nonceStr     微信支付随机串
 * @param packageValue 微信支付包名，如 prepay_id=xxx
 * @param signType     签名类型，V3 固定 RSA
 * @param paySign      微信支付签名
 */
public record PaymentPrepayVO(
        String provider,
        String prepayId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign
) {

    public static PaymentPrepayVO mock(String prepayId) {
        return new PaymentPrepayVO("MOCK", prepayId, null, null, null, null, null);
    }
}
