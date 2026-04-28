package com.nwzb.meeting_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TodoDTO {
    private Long id;

    @NotBlank(message = "待办内容不能为空")
    private String title;

    // 状态: 0-未完成, 1-已完成
    private Integer status;

    // 四象限: 1-重要紧急, 2-重要不紧急, 3-紧急不重要, 4-不重要不紧急 (默认4)
    private Integer priorityQuadrant;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime remindTime;
    private Integer remindType;
    private Integer sortOrder;

    // 父任务ID(支持子待办) 默认为0
    private Long parentId;

    // AI会议一键转存时携带
    private Long sourceMeetingId;
}
