package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.service.AuthService;
import com.coffee.vo.auth.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 体验登录接口：仅在 dev Profile 下注册，生产环境不会存在该接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Profile("dev")
public class DevAuthController {

    private final AuthService authService;

    /**
     * 体验登录：返回体验店长 Token，用于未配置微信密钥时预览完整功能
     */
    @PostMapping("/dev-login")
    public Result<LoginVO> devLogin() {
        return Result.ok(authService.devLogin());
    }
}
