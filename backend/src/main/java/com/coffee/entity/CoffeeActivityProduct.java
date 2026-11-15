package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动-商品绑定：停用用 status=0，不物理删除
 */
@Data
@TableName("coffee_activity_product")
public class CoffeeActivityProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动 ID */
    private Long activityId;

    /** 商品 ID */
    private Long productId;

    /** 秒杀基础价（分/杯），规格加价另计 */
    private Long activityPrice;

    /** 活动限量（杯） */
    private Integer activityStock;

    /** 已售（杯） */
    private Integer soldCount;

    /** 每人限购（杯） */
    private Integer limitPerUser;

    /** 绑定状态：1-生效 / 0-停用 */
    private Integer status;

    /** 排序值 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
