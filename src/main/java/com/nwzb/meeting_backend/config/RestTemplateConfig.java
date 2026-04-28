package com.nwzb.meeting_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 设置超时时间，防止 Python 侧处理太慢导致 Java 这边线程卡死
        factory.setConnectTimeout(5000); // 5秒连接超时
        factory.setReadTimeout(300000);  // 5分钟读取超时（llm向量化或者大模型总结可能需要很长时间）
        return new RestTemplate(factory);
    }
}