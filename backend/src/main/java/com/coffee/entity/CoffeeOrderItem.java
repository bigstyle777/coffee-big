package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单明细（只追加，不修改）：下单即快照，商品改价/下架不影响历史订单
 */
@Data
@TableName("coffee_order_item")
public class CoffeeOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 商品 ID（仅引用） */
    private Long productId;

    /** 秒杀活动商品 ID，普通订单为空 */
    private Long activityProductId;

    /** 商品名称快照 */
    private String productName;

    /** 商品图片快照 */
    private String productPic;

    /** 基础单价快照（分） */
    private Long basePrice;

    /** 规格加价合计（分/件） */
    private Long specExtraPrice;

    /** 规格展示文案快照，如 大杯 / 热 / 半糖 */
    private String specText;

    /** 单件价格（分）= 基础单价 + 规格加价 */
    private Long unitPrice;

    /** 购买数量 */
    private Integer quantity;

    /** 小计金额（分）= 单件价格 × 数量 */
    private Long subtotalAmount;

    /** 所选规格快照 JSON */
    private String specJson;

    private LocalDateTime createTime;
}
