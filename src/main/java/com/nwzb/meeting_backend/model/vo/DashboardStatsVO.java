package com.nwzb.meeting_backend.model.vo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
public class DashboardStatsVO {

    private List<TrendData> meetingTrend;    // 折线图：近几天会议数量趋势
    private List<PieData> speakerStats;      // 饼图：参会人数统计（如：单人、双人、多人会议占比）
    private List<PieData> topicStats;        // 饼图，会议类型统计
    private List<WordCloudData> wordCloud;   // 词云：高频主题词

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendData {
        private String date;                 // 日期，格式 yyyy-MM-dd
        private Integer count;               // 当日会议数量
        private Long duration;               // 累计时长 (秒可能会很大，用 Long 比较稳妥)
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PieData {
        private String name;                 // 分类名称（如：2人会议）
        private Integer value;               // 场次数量
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WordCloudData {
        private String name;                 // 关键词
        private Integer value;               // 出现频次（权重）
    }
}