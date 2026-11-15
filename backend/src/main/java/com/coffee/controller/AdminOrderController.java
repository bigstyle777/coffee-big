package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.admin.CancelOrderRequest;
import com.coffee.dto.admin.UpdateOrderStatusRequest;
import com.coffee.security.AdminContext;
import com.coffee.service.AdminOrderService;
import com.coffee.vo.admin.AdminOrderVO;
import com.coffee.vo.admin.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 店长后台订单管理接口
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /**
     * 订单分页查询：支持时间范围、状态、手机号与订单号 / 取餐码搜索
     */
    @GetMapping("/page")
    public Result<PageVO<AdminOrderVO>> pageOrders(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminOrderService.pageOrders(
                pageNum, pageSize, status, startTime, endTime, phone, keyword));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{orderId}")
    public Result<AdminOrderVO> getOrderDetail(@PathVariable Long orderId) {
        return Result.ok(adminOrderService.getOrderDetail(orderId));
    }

    /**
     * 状态流转：制作中 -> 待取餐 -> 已完成
     */
    @PutMapping("/status")
    public Result<Void> updateOrderStatus(@RequestBody UpdateOrderStatusRequest request) {
        adminOrderService.updateOrderStatus(AdminContext.getAdminId(), request);
        return Result.ok();
    }

    /**
     * 店长取消订单：记录取消原因，退款由后续支付退款接口处理
     */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long orderId,
                                    @RequestBody(required = false) CancelOrderRequest request) {
        adminOrderService.cancelOrder(AdminContext.getAdminId(), orderId,
                request == null ? null : request.reason());
        return Result.ok();
    }
}
