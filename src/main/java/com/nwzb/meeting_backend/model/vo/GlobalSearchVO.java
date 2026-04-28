package com.nwzb.meeting_backend.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchVO {
    private String id;               // 业务主键ID (转为String防止前端精度丢失)
    private String type;             // 数据类型："MEETING", "NOTE", "TODO"
    private String title;            // 标题或内容摘要
    private String highlightContent; // 匹配到的高亮上下文/关键词

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;// 创建时间，用于排序
}