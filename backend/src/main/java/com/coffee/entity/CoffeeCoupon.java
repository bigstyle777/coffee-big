package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券
 */
@Data
@TableName("coffee_coupon")
public class CoffeeCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 优惠券名称 */
    private String name;

    /** 优惠金额（分） */
    private Long discountAmount;

    /** 使用门槛（分），0 表示无门槛 */
    private Long minAmount;

    /** 状态：UNUSED / USED / EXPIRED */
    private String status;

    /** 生效时间 */
    private LocalDateTime startTime;

    /** 过期时间 */
    private LocalDateTime endTime;

    /** 使用的订单 ID */
    private Long usedOrderId;

    /** 使用时间 */
    private LocalDateTime usedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
