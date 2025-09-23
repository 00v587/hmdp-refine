package com.hmdp.config;

import com.hmdp.dto.Result;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.toString(), e);
        return Result.fail("服务器异常");
    }
    
    @ExceptionHandler(ExpiredJwtException.class)
    public Result handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("JWT已过期: {}", e.getMessage());
        return Result.fail("登录已过期，请重新登录");
    }
    
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("未知异常: {}", e.toString(), e);
        return Result.fail("服务器异常");
    }
}