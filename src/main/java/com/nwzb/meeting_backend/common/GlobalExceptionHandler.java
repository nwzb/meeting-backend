package com.nwzb.meeting_backend.common;

import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理客户端主动断开连接引发的异常
     * 针对浏览器获取音视频流 (audio/mpeg) 时，预加载中断导致的异常。
     * 注意：这里方法的返回值是 void，代表什么都不返回给前端，从而避免 JSON 转换冲突。
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        // 这是一个预期内的正常现象，只打印 debug 或 warn 日志，无需返回 Result
        // log.warn("客户端 (如浏览器 Audio 标签) 主动断开了流媒体连接");
    }

    /**
     * 捕获我们自己抛出的业务异常（例如：账号被封禁）
     */
    @ExceptionHandler(CustomException.class)
    public Result<?> handleCustomException(CustomException e) {
        // 如果你的 CustomException 里面存了 code，就用 e.getCode()，否则默认给 500
        Integer code = e.getCode() != null ? e.getCode() : 500;

        // 确保把错误信息塞进 Result 里发给前端
        return Result.error(e.getMessage());
    }

    /**
     * 兜底捕获其他未知系统级异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        // 打印堆栈信息到控制台，方便调试
        e.printStackTrace();
        return Result.error("服务器内部错误: " + e.getMessage());
    }
}