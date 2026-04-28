package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

// 供Controller层接收参数
@Data
public class MeetingTodoImportDTO {

    @NotNull(message = "会议ID不能为空")
    private Long meetingId;

    /**
     * 用户在前端确认或修改后的最新待办纯文本列表
     */
    private List<TodoItem> todoList;

    @Data
    public static class TodoItem {
        private String text;        // 待办内容
        private Integer quadrant;   // 四象限等级 (1-4)
    }
}