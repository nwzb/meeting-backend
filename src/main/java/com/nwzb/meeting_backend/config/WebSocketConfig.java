package com.nwzb.meeting_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {
    /**
     * 【极其关键】：这个 Bean 会自动注册所有使用了 @ServerEndpoint 注解的类
     * 如果没有它，你的 MeetingWebSocketServer 根本就不会生效，前端一连就断！
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}