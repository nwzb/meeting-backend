package com.nwzb.meeting_backend.model.vo;

import lombok.Data;

@Data
public class AdminOpsStatsVO {
    // 1. 复用普通用户的三个图表数据结构
    private DashboardStatsVO.TrendData[] meetingTrend;
    private DashboardStatsVO.PieData[] speakerStats;
    private DashboardStatsVO.WordCloudData[] wordCloud;
    private DashboardStatsVO.PieData[] topicStats;

    // 2. [新增] 运维专属：平均耗时对比 (单位: 秒)
    private ResourceStats resourceStats;

    @Data
    public static class ResourceStats {
        private Double avgAsrDuration;
        private Double avgLlmDuration;
    }
}