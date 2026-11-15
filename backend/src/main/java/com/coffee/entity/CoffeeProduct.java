package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品
 */
@Data
@TableName("coffee_product")
public class CoffeeProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属分类 ID */
    private Long categoryId;

    /** 商品名称 */
    private String name;

    /** 商品主图地址 */
    private String picUrl;

    /** 基础价格（分），不含规格加价 */
    private Long price;

    /** 库存（杯） */
    private Integer stock;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态：1-上架 / 0-下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
