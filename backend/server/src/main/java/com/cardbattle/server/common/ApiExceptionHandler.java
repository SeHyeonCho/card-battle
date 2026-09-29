package com.cardbattle.server.common;

import com.cardbattle.engine.pack.PackFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** REST API 오류를 {"code": ..., "message": ...} 형태로 돌려준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.status()).body(body(e.code(), e.getMessage()));
    }

    @ExceptionHandler(PackFormatException.class)
    public ResponseEntity<Map<String, Object>> handlePack(PackFormatException e) {
        Map<String, Object> body = body("INVALID_PACK", "카드팩에 오류가 있습니다");
        body.put("errors", e.errors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private static Map<String, Object> body(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
