package com.nwzb.meeting_backend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;


//普通用户会议列表用的 VO
@Data
public class MeetingVO {
    private Long id;
    private Long userId;
    private String title;
    private Long topicLibraryId;

    // ★ 核心新增：前端需要展示的词库名称
    private String topicLibraryName;

    private String audioUrl;
    private Long duration;
    private Integer status;
    private Integer auditStatus;
    private LocalDateTime createTime;
}