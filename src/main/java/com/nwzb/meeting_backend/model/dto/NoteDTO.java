package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoteDTO {

    // 修改时传ID，新增时不传
    private Long id;

    // 所属分类ID，允许为空（为空则代表存入"默认集合"）
    private Long collectionId;

    @NotBlank(message = "笔记标题不能为空")
    private String title;

    private String content;

    private Integer isTop;

    private Integer sortOrder;

    // 用于后续一键转存时，记录源会议的ID
    private Long sourceMeetingId;
}
