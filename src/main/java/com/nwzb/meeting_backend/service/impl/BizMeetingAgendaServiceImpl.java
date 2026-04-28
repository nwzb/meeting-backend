package com.nwzb.meeting_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.entity.BizMeetingAgenda;
import com.nwzb.meeting_backend.mapper.BizMeetingAgendaMapper;
import com.nwzb.meeting_backend.service.BizMeetingAgendaService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BizMeetingAgendaServiceImpl extends ServiceImpl<BizMeetingAgendaMapper, BizMeetingAgenda> implements BizMeetingAgendaService {

    @Override
    public List<BizMeetingAgenda> getAgendasByMeetingId(Long meetingId) {
        return this.list(new LambdaQueryWrapper<BizMeetingAgenda>()
                .eq(BizMeetingAgenda::getMeetingId, meetingId)
                .orderByAsc(BizMeetingAgenda::getTimestamp));
    }

    @Override
    public void removeByMeetingId(Long meetingId) {
        this.remove(new LambdaQueryWrapper<BizMeetingAgenda>().eq(BizMeetingAgenda::getMeetingId, meetingId));
    }
}