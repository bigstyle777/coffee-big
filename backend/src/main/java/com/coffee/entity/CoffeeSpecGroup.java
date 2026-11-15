package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规格组（杯型 / 温度 / 糖度 / 加料）
 */
@Data
@TableName("coffee_spec_group")
public class CoffeeSpecGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规格组名称 */
    private String name;

    /** 是否必选：1-必选 / 0-可选 */
    private Integer required;

    /** 是否多选：1-多选 / 0-单选 */
    private Integer multiple;

    /** 排序值，越小越靠前 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
