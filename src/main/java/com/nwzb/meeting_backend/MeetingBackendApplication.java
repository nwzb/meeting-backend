package com.nwzb.meeting_backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.nwzb.meeting_backend.mapper")
@EnableScheduling
@EnableAsync
public class MeetingBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingBackendApplication.class, args);
    }

}
