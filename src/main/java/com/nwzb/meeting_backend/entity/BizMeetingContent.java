package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("biz_meeting_content")
public class BizMeetingContent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联会议ID
     */
    private Long meetingId;

    /**
     * 切片序号 (对应 Python 里的 idx)
     */
    private Integer sliceIndex;

    /**
     * 开始秒数 (如: 180.50)
     */
    private BigDecimal startTime;

    /**
     * 结束秒数
     */
    private BigDecimal endTime;

    /**
     * 说话人标识 (如: Speaker_0)
     */
    private String speaker;

    /**
     * 识别出来的文本
     */
    private String content;
}