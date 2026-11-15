package com.coffee.service;

import com.coffee.dto.order.CreateSeckillOrderRequest;
import com.coffee.vo.order.OrderCreateVO;

/**
 * 秒杀下单业务
 */
public interface SeckillOrderService {

    /**
     * 创建秒杀订单：Redis 原子预扣活动库存，DB 事务落库，失败自动回滚
     *
     * @param userId  当前登录用户 ID
     * @param request 秒杀下单请求
     */
    OrderCreateVO createSeckillOrder(Long userId, CreateSeckillOrderRequest request);
}
