package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（顾客 / 店长）
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 角色：CUSTOMER / ADMIN */
    private String role;

    /** 状态：1-正常 / 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
