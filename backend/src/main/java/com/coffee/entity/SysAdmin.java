package com.coffee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店长后台账号：与小程序用户隔离，仅用于浏览器后台登录
 */
@Data
@TableName("sys_admin")
public class SysAdmin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 显示名称 */
    private String nickname;

    /** 状态：1-正常 / 0-禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0-正常 / 1-已删除 */
    private Integer deleted;
}
