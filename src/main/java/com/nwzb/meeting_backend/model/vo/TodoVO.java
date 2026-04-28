package com.nwzb.meeting_backend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TodoVO {
    private Long id;
    private Long userId;
    private Long sourceMeetingId;
    private String title;
    private Integer status;
    private Integer priorityQuadrant;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime remindTime;
    private Integer remindType;
    private Long parentId;
    private LocalDateTime createTime;
    private Integer sortOrder;
    // 装载子树
    private List<TodoVO> children;
    // 查关联会议标题
    private String sourceMeetingTitle;
}
