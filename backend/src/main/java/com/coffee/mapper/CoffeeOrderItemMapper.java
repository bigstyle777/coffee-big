package com.coffee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.vo.admin.HotProductRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单明细 Mapper
 */
public interface CoffeeOrderItemMapper extends BaseMapper<CoffeeOrderItem> {

    /**
     * 统计时间段内热销饮品 TOP3（按售出杯数倒序）
     */
    @Select("""
            SELECT i.product_name AS productName, SUM(i.quantity) AS totalQuantity
            FROM coffee_order_item i
            INNER JOIN coffee_order o ON o.id = i.order_id
            WHERE o.deleted = 0
              AND o.status IN ('MAKING', 'READY', 'COMPLETED')
              AND o.pay_time >= #{start}
              AND o.pay_time < #{end}
            GROUP BY i.product_name
            ORDER BY totalQuantity DESC
            LIMIT 3
            """)
    List<HotProductRow> selectHotProducts(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}
