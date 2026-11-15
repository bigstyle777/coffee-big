package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类
 */
@Data
@TableName("coffee_category")
public class CoffeeCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态：1-启用 / 0-停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
