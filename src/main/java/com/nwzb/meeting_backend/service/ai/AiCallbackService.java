package com.nwzb.meeting_backend.service.ai;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import com.nwzb.meeting_backend.model.dto.AiSliceCallbackDTO;
import com.nwzb.meeting_backend.model.dto.AiSummaryCallbackDTO;
import com.nwzb.meeting_backend.service.BizMeetingAgendaService;
import com.nwzb.meeting_backend.service.BizMeetingContentService;
import com.nwzb.meeting_backend.service.BizMeetingService;
import com.nwzb.meeting_backend.service.BizTodoService;
import com.nwzb.meeting_backend.websocket.MeetingWebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class AiCallbackService {

    @Autowired
    private BizMeetingContentService contentService;
    @Autowired
    private BizMeetingService meetingService;
    @Autowired
    private BizMeetingAgendaService agendaService;
    @Autowired
    private BizTodoService todoService;
    @Autowired
    private AiTaskQueueManager aiTaskQueueManager;

    /**
     * 处理切片入库
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAsrUpdate(AiSliceCallbackDTO sliceDTO) {
        BizMeetingContent content = new BizMeetingContent();
        content.setMeetingId(sliceDTO.getMeetingId());

        // 【关键修复1】：动态获取当前最大的 slice_index，累加 1，解决非空报错
        QueryWrapper<BizMeetingContent> qw = new QueryWrapper<>();
        qw.eq("meeting_id", sliceDTO.getMeetingId())
                .orderByDesc("slice_index")
                .last("limit 1");
        BizMeetingContent last = contentService.getOne(qw);
        int nextIndex = (last == null || last.getSliceIndex() == null) ? 1 : last.getSliceIndex() + 1;
        content.setSliceIndex(nextIndex);

        // 【关键修复2】：Python返回的是毫秒，转成秒 (保留小数)

        // 将毫秒转为秒，保留 2 位小数，并使用四舍五入
        BigDecimal divisor = new BigDecimal("1000");
        content.setStartTime(sliceDTO.getStart().divide(divisor, 2, RoundingMode.HALF_UP));
        content.setEndTime(sliceDTO.getEnd().divide(divisor, 2, RoundingMode.HALF_UP));

        content.setSpeaker(sliceDTO.getSpeaker());
        content.setContent(sliceDTO.getText());

        contentService.save(content);
    }

    /**
     * 处理总结入库 (主表 + 章节表 + 待办表)
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleSummaryUpdate(AiSummaryCallbackDTO summaryDTO) {
        String type = summaryDTO.getType();

        // 1. 根据类型决定是否落库
        if ("FINAL_SUMMARY".equals(type)) {
            // 只有最终完整版才更新数据库，保证数据完整性
            meetingService.updateMeetingSummaryByDTO(summaryDTO);
        } else {
            // "PARTIAL_SUMMARY" 部分总结：不落库，直接走到第2步推给前端即可
            log.info(">>> 收到部分阶段性总结，仅推送前端，不落库。MeetingID: {}", summaryDTO.getMeetingId());
        }

        // 2. 通过 WebSocket 实时推送给前端(需要查出 userId)
        try {
            // 先获取这篇会议对应的 userId
            BizMeeting meeting = meetingService.getById(summaryDTO.getMeetingId());
            if (meeting != null && meeting.getUserId() != null) {
                String jsonMessage = JSON.toJSONString(summaryDTO);
                MeetingWebSocketServer.sendToUser(meeting.getUserId(), jsonMessage);
            }
        } catch (Exception e) {
            log.error(">>> WebSocket 推送 AI 总结失败: {}", e.getMessage());
        }

        // 3. 释放红绿灯调度锁
        if ("FINAL_SUMMARY".equals(type)) {
            aiTaskQueueManager.releaseLock();
        }
    }

    /**
     * 处理纯状态更新 (由 Python 主动触发)
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleStatusUpdate(Long meetingId, Integer status) {
        // 1. 持久化到数据库
        BizMeeting meeting = new BizMeeting();
        meeting.setId(meetingId);
        meeting.setStatus(status);
        meetingService.updateById(meeting);

        // 2. 广播给前端
        try {
            BizMeeting dbMeeting = meetingService.getById(meetingId);
            if (dbMeeting != null && dbMeeting.getUserId() != null) {
                // 构造 WebSocket 消息体
                JSONObject msg = new JSONObject();
                msg.put("type", "STATUS_UPDATE");
                msg.put("meetingId", meetingId);
                msg.put("status", status);

                MeetingWebSocketServer.sendToUser(dbMeeting.getUserId(), msg.toJSONString());
            }
        } catch (Exception e) {
            log.error(">>> WebSocket 推送状态更新失败: {}", e.getMessage());
        }
    }

    /**
     * ======== 处理 AI 端严重错误回调 ========
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAiError(Long meetingId, String errorMsg) {
        log.error(">>> 触发错误兜底逻辑，MeetingID: {}, 错误信息: {}", meetingId, errorMsg);

        // 1. 强制将会议状态更新为 9 (失败)
        BizMeeting meeting = new BizMeeting();
        meeting.setId(meetingId);
        meeting.setStatus(9);
        meetingService.updateById(meeting);

        // 2. 通过 WebSocket 通知前端页面停止 Loading 并展示报错
        try {
            BizMeeting dbMeeting = meetingService.getById(meetingId);
            if (dbMeeting != null && dbMeeting.getUserId() != null) {
                JSONObject msg = new JSONObject();
                msg.put("type", "ERROR");
                msg.put("meetingId", meetingId);
                msg.put("message", "AI 引擎处理异常: " + errorMsg);
                msg.put("status", 9); // 同步推送最新状态

                MeetingWebSocketServer.sendToUser(dbMeeting.getUserId(), msg.toJSONString());
            }
        } catch (Exception e) {
            log.error(">>> WebSocket 推送异常状态失败: {}", e.getMessage());
        }

        // 3. 【极度关键】：释放全局队列锁，防止一条任务报错卡死整个系统
        aiTaskQueueManager.releaseLock();
        log.info("<<< 异常任务处理完毕，全局锁已释放，队列可以继续流转。");
    }
}