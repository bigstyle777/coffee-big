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
 * 店长后台拦截器：只接受 scope = ADMIN 的 admin-token
 * 小程序用户 Token 即使 role = ADMIN 也无法访问后台接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final AdminTokenStore adminTokenStore;
    private final JsonMapper jsonMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeResult(response, Result.UNAUTHORIZED_CODE, "请先登录店长后台");
            return false;
        }
        try {
            AdminTokenClaims claims = jwtUtils.parseAdminToken(
                    authorization.substring(BEARER_PREFIX.length()).trim());
            if (!adminTokenStore.isActive(claims.tokenId(), claims.adminId())) {
                writeResult(response, Result.UNAUTHORIZED_CODE, "登录已失效，请重新登录");
                return false;
            }
            AdminContext.set(new AdminSession(claims.adminId(), claims.username(), claims.tokenId()));
            return true;
        } catch (BusinessException exception) {
            log.warn("后台 Token 校验失败：{}", exception.getMessage());
            writeResult(response, exception.getCode(), exception.getMessage());
            return false;
        } catch (Exception exception) {
            log.error("后台 Token 校验异常", exception);
            writeResult(response, Result.SYSTEM_ERROR_CODE, "登录状态校验失败，请稍后重试");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        AdminContext.clear();
    }

    private void writeResult(HttpServletResponse response, int code, String message) {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(jsonMapper.writeValueAsString(Result.fail(code, message)));
        } catch (Exception exception) {
            log.error("写出后台未登录响应失败", exception);
        }
    }
}
