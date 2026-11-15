package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动：只关闭不删除
 */
@Data
@TableName("coffee_activity")
public class CoffeeActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称 */
    private String title;

    /** 活动类型：SECKILL / FULL_REDUCTION */
    private String type;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 开关：1-开启 / 0-关闭 */
    private Integer isOpen;

    /** 满减门槛（分），仅满减使用 */
    private Long fullAmount;

    /** 满减金额（分），仅满减使用 */
    private Long reduceAmount;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
