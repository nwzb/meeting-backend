package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TopicDTO {
    private Long id; // 更新时需传入，新增时不传

    @NotBlank(message = "词库名称不能为空")
    private String name;

    private String description;

    private Integer isPublic; // 1-公开, 0-不公开
}