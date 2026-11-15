package com.coffee.security;

import com.coffee.common.BusinessException;
import com.coffee.common.Result;

/**
 * 当前后台请求的店长上下文
 * 由后台拦截器写入，请求结束后清空
 */
public final class AdminContext {

    private static final ThreadLocal<AdminSession> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(AdminSession session) {
        HOLDER.set(session);
    }

    public static AdminSession getSession() {
        AdminSession session = HOLDER.get();
        if (session == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "登录状态已失效，请重新登录");
        }
        return session;
    }

    public static Long getAdminId() {
        return getSession().adminId();
    }

    public static String getUsername() {
        return getSession().username();
    }

    public static String getTokenId() {
        return getSession().tokenId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
