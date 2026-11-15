package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.auth.WxLoginRequest;
import com.coffee.service.AuthService;
import com.coffee.vo.auth.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信小程序登录
     */
    @PostMapping("/wx-login")
    public Result<LoginVO> wxLogin(@RequestBody WxLoginRequest request) {
        return Result.ok(authService.wxLogin(request));
    }
}
