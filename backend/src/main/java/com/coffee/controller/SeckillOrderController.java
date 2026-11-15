package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.order.CreateSeckillOrderRequest;
import com.coffee.security.UserContext;
import com.coffee.service.SeckillOrderService;
import com.coffee.vo.order.OrderCreateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客端秒杀下单接口
 */
@RestController
@RequestMapping("/api/order/seckill")
@RequiredArgsConstructor
public class SeckillOrderController {

    private final SeckillOrderService seckillOrderService;

    /**
     * 创建秒杀订单：活动价、库存与限购全部由后端校验
     */
    @PostMapping
    public Result<OrderCreateVO> createSeckillOrder(@RequestBody CreateSeckillOrderRequest request) {
        return Result.ok(seckillOrderService.createSeckillOrder(UserContext.getUserId(), request));
    }
}
