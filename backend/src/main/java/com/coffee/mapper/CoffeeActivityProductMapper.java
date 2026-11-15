package com.coffee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coffee.entity.CoffeeActivityProduct;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 活动-商品绑定 Mapper
 */
public interface CoffeeActivityProductMapper extends BaseMapper<CoffeeActivityProduct> {

    /**
     * 原子递增活动销量：DB 侧最终防超卖，影响 0 行说明活动库存不足
     */
    @Update("""
            UPDATE coffee_activity_product
            SET sold_count = sold_count + #{quantity}
            WHERE id = #{bindingId}
              AND status = 1
              AND sold_count + #{quantity} <= activity_stock
            """)
    int incrementSoldCount(@Param("bindingId") Long bindingId, @Param("quantity") int quantity);

    /**
     * 取消订单时回滚活动销量
     */
    @Update("""
            UPDATE coffee_activity_product
            SET sold_count = sold_count - #{quantity}
            WHERE id = #{bindingId}
              AND sold_count >= #{quantity}
            """)
    int decreaseSoldCount(@Param("bindingId") Long bindingId, @Param("quantity") int quantity);
}
