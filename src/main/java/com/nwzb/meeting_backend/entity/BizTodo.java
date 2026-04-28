package com.nwzb.meeting_backend.entity;

import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_todo")
public class BizTodo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceMeetingId;

    /**
     * 待办内容 (AI 提取的 task 描述)
     */
    @JSONField(name = "task")
    private String title;

    /**
     * 状态: 0-未完成, 1-已完成
     */
    private Integer status;

    /**
     * 四象限优先级: 1-重要紧急, 2-重要不紧急, 3-紧急不重要, 4-不重要不紧急
     */
    private Integer priorityQuadrant;

    /**
     * 截止时间
     */
    private LocalDateTime deadline;

    /**
     * 提醒时间
     */
    private LocalDateTime remindTime;

    /**
     * 提醒方式: 0-不提醒, 1-单次, 2-每天, 3-每周, 4-每月
     */
    @TableField("remind_type")
    private Integer remindType;

    /**
     * 父任务ID (用于实现任务拆解，默认 0 为顶级任务)
     */
    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Integer sortOrder;
}