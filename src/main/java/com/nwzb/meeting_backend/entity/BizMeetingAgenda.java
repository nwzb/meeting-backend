package com.nwzb.meeting_backend.entity;

import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("biz_meeting_agenda")
public class BizMeetingAgenda {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meetingId;

    /**
     * 跳转时间戳 (秒)，对应前端播放器的控制逻辑
     */
    private BigDecimal timestamp;

    /**
     * 告诉 Fastjson，Python 传过来的 "topic" 请塞进这个 "title" 字段里
     */
    @JSONField(name = "topic")
    private String title;

    /**
     * 该章节的简要总结
     */
    private String summary;
}