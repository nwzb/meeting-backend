package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizMeetingAgenda;
import java.util.List;

public interface BizMeetingAgendaService extends IService<BizMeetingAgenda> {
    /**
     * 获取某场会议的所有章节，按时间先后排序
     */
    List<BizMeetingAgenda> getAgendasByMeetingId(Long meetingId);

    void removeByMeetingId(Long meetingId);
}