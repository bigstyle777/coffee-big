package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单主表
 */
@Data
@TableName("coffee_order")
public class CoffeeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号，全局唯一 */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 订单状态：PENDING_PAY / MAKING / READY / COMPLETED / CANCELLED */
    private String status;

    /** 订单类型：NORMAL / SECKILL */
    private String orderType;

    /** 取餐方式：SELF_PICKUP / DINE_IN */
    private String pickupType;

    /** 商品总额（分），后端按库重算 */
    private Long totalAmount;

    /** 优惠金额（分） */
    private Long discountAmount;

    /** 活动优惠金额（分） */
    private Long activityDiscountAmount;

    /** 使用的优惠券 ID */
    private Long couponId;

    /** 使用的活动 ID */
    private Long activityId;

    /** 客户端幂等键 */
    private String requestId;

    /** 实付金额（分），后端按库重算 */
    private Long payAmount;

    /** 商品总件数 */
    private Integer itemCount;

    /** 取餐码，支付成功后生成，如 A001 */
    private String pickupCode;

    /** 顾客备注 */
    private String remark;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 支付渠道：MOCK / WECHAT */
    private String payChannel;

    /** 支付平台预支付 ID */
    private String prepayId;

    /** 支付平台交易号 */
    private String transactionId;

    /** 退款状态：NONE / REFUNDING / REFUNDED / FAILED */
    private String refundStatus;

    /** 退款单号 */
    private String refundNo;

    /** 退款金额（分） */
    private Long refundAmount;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
