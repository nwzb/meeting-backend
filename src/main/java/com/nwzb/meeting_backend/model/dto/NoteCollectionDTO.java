package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoteCollectionDTO {

    // 修改时需要传ID，新增时不传
    private Long id;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Integer sortOrder;
}
