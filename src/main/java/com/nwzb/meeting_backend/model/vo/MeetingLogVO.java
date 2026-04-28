package com.nwzb.meeting_backend.model.vo;

import com.nwzb.meeting_backend.entity.BizMeeting;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运维管理大屏 - 会议日志视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MeetingLogVO extends BizMeeting {

    /**
     * 关联的用户名 (来自 sys_user 表)
     */
    private String username;
}