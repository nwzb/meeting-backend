package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_meeting")
public class BizMeeting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private Long topicLibraryId;

    private String audioUrl;

    private Long duration;

    private Integer asrDuration;
    private Integer llmDuration;

    /**
     * AI处理流程状态: 0-上传中, 1-排队中, 2-ASR识别中, 3-LLM总结中, 4-完成, 9-失败
     * 特别注意：Python 触发插队总结时，此状态应改为 3
     */
    private Integer status;

    /**
     * 审计管控状态: 0-正常, 1-已归档(只读), 2-违规屏蔽(前端隐藏内容)
     */
    private Integer auditStatus;

    private String auditReason;

    private Integer sensitiveWordCount;

    /**
     * AI生成的全文纪要(Markdown格式)
     * 使用 mediumtext 对应 String 即可，MP 自动映射
     */
    private String fullSummary;

    private String aiKeywords;

    /**
     * AI提取的待办事项(JSON数组格式，如 ["待办1", "待办2"])
     */
    private String aiTodos;

    /**
     * 记录当前会议是否将逐字稿向量化
     */
    @TableField("is_vectorized")
    private Integer isVectorized;

    @TableField(fill = FieldFill.INSERT) // 自动填充创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}