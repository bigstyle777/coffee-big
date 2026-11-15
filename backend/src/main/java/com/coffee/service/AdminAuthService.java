package com.coffee.service;

import com.coffee.dto.admin.AdminLoginRequest;
import com.coffee.vo.admin.AdminInfoVO;
import com.coffee.vo.admin.AdminLoginVO;

/**
 * 店长后台认证
 */
public interface AdminAuthService {

    /**
     * 账号密码登录，成功后返回 admin-token
     */
    AdminLoginVO login(AdminLoginRequest request);

    /**
     * 退出登录：把当前 token 从 Redis 白名单移除，立即失效
     */
    void logout();

    /**
     * 查询当前登录店长信息
     */
    AdminInfoVO getCurrentAdmin();
}
