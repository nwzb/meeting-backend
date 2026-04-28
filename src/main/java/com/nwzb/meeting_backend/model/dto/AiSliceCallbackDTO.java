package com.nwzb.meeting_backend.model.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 严格对应架构：接收 Python 单个 ASR 切片 JSON
 */
@Data
public class AiSliceCallbackDTO {
    private Long meetingId;

    private String type;    // 用于前端判断路由事件

    private BigDecimal start; // 开始时间(秒)
    private BigDecimal end;   // 结束时间(秒)
    private String speaker;
    private String text;
}