package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流水（只追加，事务提交后异步写入）
 */
@Data
@TableName("coffee_order_log")
public class CoffeeOrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 变更前状态，创建订单时为 null */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 操作人用户 ID，系统自动流转时为 null */
    private Long operatorId;

    /** 操作人角色：CUSTOMER / ADMIN / SYSTEM */
    private String operatorRole;

    /** 操作备注 */
    private String remark;

    private LocalDateTime createTime;
}
