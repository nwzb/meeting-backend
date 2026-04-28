package com.nwzb.meeting_backend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MeetingAuditVO {
    private Long id;
    private String title;
    private Long userId;
    private String username;            // 需跨表组装：所属用户姓名
    private Long duration;

    private Integer sensitiveWordCount; // 敏感词触发总数
    private String topicLibraryName;    // 需跨表组装：所属词库名称

    private Integer auditStatus;
    private String auditReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}