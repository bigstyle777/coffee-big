package com.coffee.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：保证任何异常都以统一 Result 结构返回
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：属于可预期的用户输入问题，只返回提示，不打印堆栈
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        log.warn("业务异常：{}", exception.getMessage());
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 参数格式错误：如 JSON 解析失败、路径参数类型不匹配
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleBadRequest(Exception exception) {
        log.warn("请求参数解析失败：{}", exception.getMessage());
        return Result.fail(Result.BUSINESS_ERROR_CODE, "请求参数格式不正确");
    }

    /**
     * 兜底异常：记录完整堆栈，避免把内部细节暴露给前端
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("系统异常", exception);
        return Result.fail(Result.SYSTEM_ERROR_CODE, "系统繁忙，请稍后重试");
    }
}
