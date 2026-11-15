package com.coffee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coffee.entity.CoffeeProduct;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper
 */
public interface CoffeeProductMapper extends BaseMapper<CoffeeProduct> {

    /**
     * 原子扣减库存：库存不足时返回 0，由事务保证失败后整体回滚
     */
    @Update("""
            UPDATE coffee_product
            SET stock = stock - #{quantity}
            WHERE id = #{productId}
              AND status = 1
              AND deleted = 0
              AND stock >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 恢复库存：取消订单 / 超时关单时调用
     */
    @Update("""
            UPDATE coffee_product
            SET stock = stock + #{quantity}
            WHERE id = #{productId}
              AND deleted = 0
            """)
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
