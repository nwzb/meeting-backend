package com.nwzb.meeting_backend.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MeetingAuditDTO {
    @NotNull(message = "会议ID不能为空")
    private Long meetingId;

    @NotNull(message = "审查状态不能为空")
    private Integer auditStatus; // 0-正常, 1-已归档(只读), 2-违规屏蔽

    private String auditReason; // 屏蔽原因
}