package com.nwzb.meeting_backend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoteVO {
    private Long id;
    private Long collectionId;
    private String title;
    private String content;
    private Integer isTop;
    private Integer sortOrder;
    private Long sourceMeetingId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
