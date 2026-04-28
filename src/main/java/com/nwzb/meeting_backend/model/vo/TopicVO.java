package com.nwzb.meeting_backend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TopicVO {
    private Long id;

    private String name;

    private String description;

    private Integer isPublic; // 1-公开, 0-仅管理员可见

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}