package com.nl2sql.common.exception;

import com.nl2sql.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getCode() == 401) status = HttpStatus.UNAUTHORIZED;
        else if (ex.getCode() == 403) status = HttpStatus.FORBIDDEN;
        else if (ex.getCode() == 429) status = HttpStatus.TOO_MANY_REQUESTS;
        else if (ex.getCode() >= 500) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(R.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleAll(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(R.fail(500, "服务异常: " + ex.getMessage()));
    }
}
