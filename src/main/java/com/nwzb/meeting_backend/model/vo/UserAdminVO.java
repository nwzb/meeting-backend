package com.nwzb.meeting_backend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserAdminVO {
    private Long id;
    private String username;
    private String avatar;
    private Integer role;
    private LocalDateTime createTime;

    // 会议数量
    private Integer totalMeetingCount;
    private Integer weekMeetingCount;

    // 音频时长 (秒)
    private Long totalAudioDuration;
    private Long weekAudioDuration;

    // AI算力消耗时长 (秒)
    private Long totalAiDuration;
    private Long weekAiDuration;
}