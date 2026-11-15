package com.coffee.security;

import com.coffee.common.BusinessException;
import com.coffee.common.Result;

/**
 * 当前请求的登录用户上下文
 * 由登录拦截器写入，请求结束后清空，避免线程复用导致数据串号
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser getLoginUser() {
        LoginUser loginUser = HOLDER.get();
        if (loginUser == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "登录状态已失效，请重新登录");
        }
        return loginUser;
    }

    public static Long getUserId() {
        return getLoginUser().userId();
    }

    public static String getRole() {
        return getLoginUser().role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
