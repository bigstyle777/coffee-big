package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规格选项（大杯 / 热 / 半糖 / 燕麦奶……）
 */
@Data
@TableName("coffee_spec_option")
public class CoffeeSpecOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属规格组 ID */
    private Long groupId;

    /** 选项名称 */
    private String name;

    /** 加价（分），0 表示不加价 */
    private Long extraPrice;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态：1-启用 / 0-停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
