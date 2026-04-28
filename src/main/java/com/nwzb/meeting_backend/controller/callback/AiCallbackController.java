package com.nwzb.meeting_backend.controller.callback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.entity.BizMeeting;
import com.nwzb.meeting_backend.model.dto.AiSliceCallbackDTO;
import com.nwzb.meeting_backend.model.dto.AiSummaryCallbackDTO;
import com.nwzb.meeting_backend.service.BizMeetingService;
import com.nwzb.meeting_backend.service.ai.AiCallbackService;
import com.nwzb.meeting_backend.websocket.MeetingWebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/meeting/callback")
public class AiCallbackController {

    @Autowired
    private AiCallbackService aiCallbackService;
    @Autowired
    private BizMeetingService meetingService;
    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping
    public Result<String> onAiCallback(@RequestBody Map<String, Object> rawData) {
        // 将 meetingId 提取到外层，以便在 catch 块中能拿到它进行兜底补偿
        Long meetingId = null;
        try {
            log.info(">>> 收到 AI 回调，原始报文: {}", rawData);

            String type = (String) rawData.get("type");
            // 健壮性：防止 rawData 里没有 meetingId 导致空指针
            if (rawData.get("meetingId") == null) {
                log.error("回调报文非法：缺失 meetingId");
                return Result.error("缺失 meetingId");
            }
            meetingId = Long.valueOf(rawData.get("meetingId").toString());

            BizMeeting meeting = meetingService.getById(meetingId);
            if (meeting == null) {
                log.error("回调失败：找不到会议 ID {}", meetingId);
                return Result.error("会议不存在");
            }

            Object payloadData = rawData.get("data");

            if ("ASR_UPDATE".equals(type)) {
                List<AiSliceCallbackDTO> sliceList = objectMapper.convertValue(
                        payloadData, new TypeReference<List<AiSliceCallbackDTO>>() {}
                );

                for (AiSliceCallbackDTO sliceDTO : sliceList) {
                    sliceDTO.setMeetingId(meetingId);
                    sliceDTO.setType("ASR_UPDATE");
                    // 1. 逐字稿落库
                    aiCallbackService.handleAsrUpdate(sliceDTO);
                    // 2. 逐字稿推流
                    MeetingWebSocketServer.sendToUser(meeting.getUserId(), objectMapper.writeValueAsString(sliceDTO));
                }

            } else if (type != null && type.endsWith("SUMMARY")) {
                // 【潜在崩溃点】：如果 Python 传来的 JSON 格式不符合 DTO 规范，这里会抛出异常
                AiSummaryCallbackDTO summaryDTO = objectMapper.convertValue(payloadData, AiSummaryCallbackDTO.class);
                summaryDTO.setMeetingId(meetingId);
                summaryDTO.setType(type);

                // 提取耗时数据
                if (rawData.containsKey("asrDuration") && rawData.get("asrDuration") != null) {
                    summaryDTO.setAsrDuration((Integer) rawData.get("asrDuration"));
                }
                if (rawData.containsKey("llmDuration") && rawData.get("llmDuration") != null) {
                    summaryDTO.setLlmDuration((Integer) rawData.get("llmDuration"));
                }

                // 交给 Service 处理 (Service 内部需判断：如果是 FINAL_SUMMARY，记得释放全局锁！)
                aiCallbackService.handleSummaryUpdate(summaryDTO);

            } else if ("STATUS_UPDATE".equals(type)) {
                Map<String, Object> dataMap = (Map<String, Object>) payloadData;
                Integer status = (Integer) dataMap.get("status");
                aiCallbackService.handleStatusUpdate(meetingId, status);

            } else if ("ERROR".equals(type)) {
                // 拦截 Python 端的崩溃报错
                String errorMsg = String.valueOf(payloadData);
                log.error("❌ 收到 AI 端严重错误回调，会议ID: {}, 报错: {}", meetingId, errorMsg);
                aiCallbackService.handleAiError(meetingId, errorMsg);

            } else {
                log.warn("收到未知类型的回调: {}", type);
            }

            return Result.success("Callback processed");

        } catch (Exception e) {
            log.error("❌ 处理 AI 回调发生异常: ", e);

            // ★ 核心修复：Java 端解析崩溃时的死信防线
            if (meetingId != null) {
                log.warn("🚨 触发 Java 端容灾补偿机制，将会议 {} 标记为失败并释放锁。", meetingId);
                // 直接复用你刚才写好的 handleAiError 方法！
                aiCallbackService.handleAiError(meetingId, "JSON数据解析失败或格式异常: " + e.getMessage());
            } else {
                log.error("🚨 无法执行容灾补偿：连 meetingId 都未能成功提取。");
            }

            return Result.error("回调处理失败，已触发补偿机制");
        }
    }
}