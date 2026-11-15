package com.coffee.service;

import com.coffee.dto.order.CreateOrderRequest;
import com.coffee.vo.order.OrderCreateVO;
import com.coffee.vo.order.OrderVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单业务
 */
public interface OrderService {

    /**
     * 创建订单（待支付）
     * 金额完全由后端按商品、规格快照重算，不信任前端传入的任何金额
     *
     * @param userId  下单用户 ID
     * @param request 下单请求，只包含商品/规格 ID 与数量
     * @return 订单概要
     */
    OrderCreateVO createOrder(Long userId, CreateOrderRequest request);

    /**
     * 支付成功统一处理器
     * Mock 支付确认与真实微信支付回调都走这里，保证订单状态流转一致
     *
     * @param orderId       订单 ID
     * @param payChannel    支付渠道：MOCK / WECHAT
     * @param transactionId 支付平台交易号
     * @param paidAmount    实付金额（分）
     */
    void confirmPaymentSuccess(Long orderId, String payChannel,
                               String transactionId, long paidAmount);

    /**
     * 查询当前用户的订单列表（按下单时间倒序，最多 50 条）
     *
     * @param userId 当前登录用户 ID
     * @param status 订单状态筛选，可为空
     */
    List<OrderVO> listMyOrders(Long userId, String status);

    /**
     * 查询当前用户的订单详情
     *
     * @param userId  当前登录用户 ID
     * @param orderId 订单 ID
     */
    OrderVO getOrderDetail(Long userId, Long orderId);

    /**
     * 顾客取消未支付订单
     *
     * @param userId  当前登录用户 ID
     * @param orderId 订单 ID
     */
    void cancelOrder(Long userId, Long orderId);

    /**
     * 超时关单：取消下单时间早于 cutoff 的待支付订单，并回滚库存与优惠券
     *
     * @param orderId 订单 ID
     * @param cutoff  超时截止时间
     * @return 是否成功关闭
     */
    boolean cancelExpiredOrder(Long orderId, LocalDateTime cutoff);

    /**
     * 订单取消后回滚库存与优惠券，供店长后台取消订单复用
     *
     * @param orderId 订单 ID
     */
    void restoreResourcesForCancelledOrder(Long orderId);
}
