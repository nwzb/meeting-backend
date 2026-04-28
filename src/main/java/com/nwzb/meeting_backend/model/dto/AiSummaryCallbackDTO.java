package com.nwzb.meeting_backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AiSummaryCallbackDTO {

    // 这两个字段是 AiCallbackController 补进来的
    private Long meetingId;
    private String type;

    // 接收 Python 端传来的耗时统计（单位：秒）
    private Integer asrDuration;
    private Integer llmDuration;

    // 对齐 LLM 返回的 JSON
    private String title;
    private String summary;
    private List<String> keywords;
    private List<AiChapterDTO> chapters;

    // 使用 JsonProperty 映射下划线命名到 Java 的驼峰命名
    @JsonProperty("action_items")
    private List<String> actionItems;

    @Data
    public static class AiChapterDTO {
        private String topic;
        private String content;
        private Double timestamp;
    }
}