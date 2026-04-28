package com.nwzb.meeting_backend.common;

import lombok.Getter;

/**
 * 业务自定义异常
 */
@Getter
public class CustomException extends RuntimeException {
    private final Integer code;

    public CustomException(String message) {
        super(message);
        this.code = 400; // 默认业务错误码
    }

    public CustomException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}