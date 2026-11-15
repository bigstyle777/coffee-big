package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.order.CreateOrderRequest;
import com.coffee.security.UserContext;
import com.coffee.service.OrderService;
import com.coffee.vo.order.OrderCreateVO;
import com.coffee.vo.order.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客端订单接口
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单：金额由后端重算，取餐码由 Redis 按日递増
     */
    @PostMapping("/create")
    public Result<OrderCreateVO> createOrder(@RequestBody CreateOrderRequest request) {
        return Result.ok(orderService.createOrder(UserContext.getUserId(), request));
    }

    /**
     * 我的订单列表，可按状态筛选
     */
    @GetMapping("/list")
    public Result<List<OrderVO>> listMyOrders(@RequestParam(required = false) String status) {
        return Result.ok(orderService.listMyOrders(UserContext.getUserId(), status));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long orderId) {
        return Result.ok(orderService.getOrderDetail(UserContext.getUserId(), orderId));
    }

    /**
     * 取消未支付订单
     */
    @PostMapping("/cancel/{orderId}")
    public Result<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(UserContext.getUserId(), orderId);
        return Result.ok();
    }
}
