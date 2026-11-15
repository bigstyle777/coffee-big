package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.admin.AdminLoginRequest;
import com.coffee.service.AdminAuthService;
import com.coffee.vo.admin.AdminInfoVO;
import com.coffee.vo.admin.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店长后台认证接口
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 账号密码登录，返回 admin-token
     */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody AdminLoginRequest request) {
        return Result.ok(adminAuthService.login(request));
    }

    /**
     * 退出登录，admin-token 立即失效
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        adminAuthService.logout();
        return Result.ok();
    }

    /**
     * 当前登录店长信息
     */
    @GetMapping("/profile")
    public Result<AdminInfoVO> profile() {
        return Result.ok(adminAuthService.getCurrentAdmin());
    }
}
