package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品-规格组绑定：控制某个商品可用的规格组及展示顺序
 */
@Data
@TableName("coffee_product_spec_group")
public class CoffeeProductSpecGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 规格组 ID */
    private Long groupId;

    /** 该商品下规格组的展示顺序 */
    private Integer sort;

    private LocalDateTime createTime;
}
