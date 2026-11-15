package com.coffee.common;

/**
 * 业务异常：由业务规则主动抛出，统一被全局异常处理器转成 Result
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(Result.BUSINESS_ERROR_CODE, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
