package com.coffee.service;

import com.coffee.dto.admin.UpdateOrderStatusRequest;
import com.coffee.vo.admin.AdminOrderVO;
import com.coffee.vo.admin.PageVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店长订单后台
 */
public interface AdminOrderService {

    /**
     * 订单分页查询
     *
     * @param status    订单状态，可为空
     * @param startTime 下单时间起，可为空
     * @param endTime   下单时间止，可为空
     * @param phone     顾客手机号，可为空
     * @param keyword   订单号 / 取餐码，可为空
     */
    PageVO<AdminOrderVO> pageOrders(long pageNum, long pageSize, String status,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    String phone, String keyword);

    /**
     * 订单详情
     */
    AdminOrderVO getOrderDetail(Long orderId);

    /**
     * 最近订单，供首页看板展示
     */
    List<AdminOrderVO> listRecentOrders(int limit);

    /**
     * 店长流转订单状态：MAKING -> READY -> COMPLETED
     *
     * @param adminId 后台账号 ID
     * @param request 状态更新请求
     */
    void updateOrderStatus(Long adminId, UpdateOrderStatusRequest request);

    /**
     * 店长取消订单：回滚库存与优惠券，并记录退款待处理流水
     *
     * @param adminId 后台账号 ID
     * @param orderId 订单 ID
     * @param reason  取消原因
     */
    void cancelOrder(Long adminId, Long orderId, String reason);
}
