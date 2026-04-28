package com.nwzb.meeting_backend.websocket;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心 WebSocket 服务端
 * 路径: ws://localhost:8080/websocket/meeting/{userId}
 */
@Slf4j
@Component
@ServerEndpoint("/websocket/meeting/{userId}")
public class MeetingWebSocketServer {

    // 静态变量，保存每个用户的 Session 对象
    private static final ConcurrentHashMap<Long, Session> SESSION_POOL = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        // 强制设置空闲超时时间为 10 分钟 (600000毫秒)，防止长时间没说话被 Tomcat 踢下线
        session.setMaxIdleTimeout(600000L);
        SESSION_POOL.put(userId, session);
        log.info("【WebSocket】连接成功，用户ID: {}, 当前在线人数: {}", userId, SESSION_POOL.size());
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") Long userId) {
        // 【修复】：只有当 Map 中的 session 确实是当前断开的这个旧 session 时，才执行移除。
        // 防止页面刷新时，旧连接延迟关闭把新连接的 Session 给误删了！
        SESSION_POOL.computeIfPresent(userId, (key, existingSession) -> {
            if (existingSession.equals(session)) {
                return null; // 确认是同一个，安全移除
            }
            return existingSession; // 不是同一个（说明新连接已经顶替上来了），保留新连接
        });
        log.info("【WebSocket】连接断开，用户ID: {}, 当前在线人数: {}", userId, SESSION_POOL.size());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if ("ping".equals(message)) {
            try {
                // 【修复】：统一使用 getAsyncRemote() 进行心跳回包，防止与业务推送发生并发冲突抛错
                session.getAsyncRemote().sendText("pong");
            } catch (Exception e) {
                log.error("WebSocket心跳回包异常", e);
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("【WebSocket】发生错误: {}", error.getMessage());
    }

    /**
     * 向指定用户发送消息 (用于 ASR 推送或待办提醒)
     */
    public static void sendToUser(Long userId, String message) {
        Session session = SESSION_POOL.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 使用 getAsyncRemote 防止大规模推流时阻塞
                session.getAsyncRemote().sendText(message);
            } catch (Exception e) {
                log.error("【WebSocket】推送失败: {}", e.getMessage());
            }
        }
    }

    public void sendAll(String message) {
        SESSION_POOL.forEach((id, session) -> {
            if (session.isOpen()) session.getAsyncRemote().sendText(message);
        });
    }
}