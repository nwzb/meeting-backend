package com.nwzb.meeting_backend.model.vo;

import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.entity.BizMeetingAgenda;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import lombok.Data;
import java.util.List;

@Data
public class MeetingDetailVO {
    private BizMeeting meeting;                 // 会议主表信息
    private List<BizMeetingContent> contents;   // 逐字稿列表 (按时间升序)
    private List<BizMeetingAgenda> agendas;     // 智能章节列表 (按时间升序)
    private List<String> aiTodos;               // AI提取的待办事项

    private List<String> sensitiveWords;
}