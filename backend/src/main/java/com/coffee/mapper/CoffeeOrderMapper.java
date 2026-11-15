package com.coffee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coffee.entity.CoffeeOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 订单 Mapper
 */
public interface CoffeeOrderMapper extends BaseMapper<CoffeeOrder> {

    /**
     * 统计时间段内创建的订单总数（含未支付与已取消）
     */
    @Select("""
            SELECT COUNT(*)
            FROM coffee_order
            WHERE deleted = 0
              AND create_time >= #{start}
              AND create_time < #{end}
            """)
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计时间段内的实付营收（分），只算已支付订单
     */
    @Select("""
            SELECT IFNULL(SUM(pay_amount), 0)
            FROM coffee_order
            WHERE deleted = 0
              AND status IN ('MAKING', 'READY', 'COMPLETED')
              AND pay_time >= #{start}
              AND pay_time < #{end}
            """)
    Long sumPaidAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计时间段内已出餐订单数（待取餐 + 已完成）
     */
    @Select("""
            SELECT COUNT(*)
            FROM coffee_order
            WHERE deleted = 0
              AND status IN ('READY', 'COMPLETED')
              AND create_time >= #{start}
              AND create_time < #{end}
            """)
    long countReadyBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计时间段内待制作订单数
     */
    @Select("""
            SELECT COUNT(*)
            FROM coffee_order
            WHERE deleted = 0
              AND status = 'MAKING'
              AND create_time >= #{start}
              AND create_time < #{end}
            """)
    long countMakingBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
