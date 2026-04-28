package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import com.nwzb.meeting_backend.model.dto.AiSliceCallbackDTO;

import java.util.List;

public interface BizMeetingContentService extends IService<BizMeetingContent> {
    /**
     * 根据会议ID按时间顺序查询逐字稿
     */
    void saveAsrSlices(Long meetingId, Object data);
    List<BizMeetingContent> getByMeetingId(Long meetingId);
    void saveSingleSlice(AiSliceCallbackDTO sliceDTO);
}