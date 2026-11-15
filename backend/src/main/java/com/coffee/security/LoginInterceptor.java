package com.coffee.security;

import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：解析 Authorization 头中的 Bearer Token，写入 UserContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final JsonMapper jsonMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求，避免 H5 调试时被拦截
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeResult(response, Result.UNAUTHORIZED_CODE, "未登录或登录已过期");
            return false;
        }
        try {
            LoginUser loginUser = jwtUtils.parseLoginUser(authorization.substring(BEARER_PREFIX.length()).trim());
            UserContext.set(loginUser);
            return true;
        } catch (BusinessException exception) {
            log.warn("Token 校验失败：{}", exception.getMessage());
            writeResult(response, exception.getCode(), exception.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        UserContext.clear();
    }

    private void writeResult(HttpServletResponse response, int code, String message) {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(jsonMapper.writeValueAsString(Result.fail(code, message)));
        } catch (Exception exception) {
            log.error("写出未登录响应失败", exception);
        }
    }
}
