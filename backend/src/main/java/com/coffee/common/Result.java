package com.coffee.common;

/**
 * 统一响应结构：前端只认这一种返回格式
 *
 * @param code    业务状态码，200 表示成功
 * @param message 提示信息
 * @param data    业务数据
 */
public record Result<T>(int code, String message, T data) {

    public static final int SUCCESS_CODE = 200;
    public static final int BUSINESS_ERROR_CODE = 400;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FORBIDDEN_CODE = 403;
    public static final int SYSTEM_ERROR_CODE = 500;

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
