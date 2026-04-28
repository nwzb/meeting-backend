package com.nwzb.meeting_backend.model.vo;

import lombok.Data;

@Data
public class OpsMonitorVO {
    private Double cpuUsage;         // CPU 使用率 (%)
    private Double ramUsage;         // 内存 使用率 (%)
    private Double vramUsage;        // 显存 使用率 (%)
    private Boolean isAiRunning;     // AI 是否正在运行任务
    private String currentMeetingId; // 当前正在处理的会议ID（如果有）
    private Long aiNetworkLatency; // Java到Python引擎的通信延迟(ms)
}