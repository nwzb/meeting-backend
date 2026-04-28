package com.nwzb.meeting_backend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WordVO {
    private Long id;

    private Long libraryId;

    // ★ 新增字段：主题词库名称，前端表格直接展示
    private String libraryName;

    private String word;

    private Integer type; // 1-热词修正, 2-敏感词

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}