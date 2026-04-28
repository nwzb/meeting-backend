package com.nwzb.meeting_backend.model.dto;

import com.nwzb.meeting_backend.entity.BizMeetingAgenda;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import lombok.Data;
import java.util.List;

@Data
public class MeetingSaveDTO {
    // 整体纪要
    private String fullSummary;
    // 待办事项 (纯文本列表)
    private List<String> aiTodos;
    // 逐字稿列表 (用户可能修改了文字或说话人)
    private List<BizMeetingContent> contents;
    // 章节大纲列表 (用户可能修改了标题或摘要)
    private List<BizMeetingAgenda> agendas;
}