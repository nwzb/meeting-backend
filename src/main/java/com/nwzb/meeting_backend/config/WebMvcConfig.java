package com.nwzb.meeting_backend.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;

/**
 * Web 配置中心：负责跨域与拦截器注册
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Value("${file.upload-path}")
    private String uploadPath;

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**") // 默认拦截所有请求
                .excludePathPatterns(
                        "/auth/login",          // 登录接口放行
                        "/auth/register",       // 注册接口放行
                        "/uploads/**",          // 放行音频文件
                        "/websocket/**",        // WebSocket握手放行
                        "/meeting/callback",    // AI的内部回调放行
                        "/error"                // 错误页面放行
                );
    }

    /**
     * 解决跨域问题（Vue3 5174端口 调 SpringBoot 8080端口）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 允许所有来源，生产环境可改为具体域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意：file: 后面必须跟具体的物理路径，且最后要带斜杠
        String filePath = "file:" + uploadPath;
        if (!filePath.endsWith("/")) {
            filePath += "/";
        }

        // 将网络虚拟路径 /uploads/audio/** 映射到 磁盘真实路径
        // 剪切：/uploads/audio/**.mp3 - /uploads/ = audio/**.mp3
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(filePath);
    }

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
            // 配置序列化 (后端 -> 前端)
            builder.serializers(new LocalDateTimeSerializer(formatter));
            // 配置反序列化 (前端 -> 后端)
            builder.deserializers(new LocalDateTimeDeserializer(formatter));
        };
    }
}