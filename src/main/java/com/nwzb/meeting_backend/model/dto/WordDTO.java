package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WordDTO {
    private Long id;

    @NotNull(message = "必须指定所属词库ID")
    private Long libraryId;

    @NotBlank(message = "词汇内容不能为空")
    private String word;

    @NotNull(message = "词汇类型不能为空")
    private Integer type; // 1-热词修正, 2-敏感词
}