package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_note_collection")
public class BizNoteCollection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 分类名称 (如: 工作日志, 毕设灵感)
     */
    private String name;

    /**
     * 排序优先级 (数字越小越靠前)
     */
    private Integer sortOrder;
}