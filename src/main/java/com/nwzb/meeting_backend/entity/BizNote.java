package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_note")
public class BizNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 所属分类ID (对应 biz_note_collection)
     */
    private Long collectionId;

    private String title;

    /**
     * 笔记内容 (longtext 对应 String)
     */
    private String content;

    /**
     * 是否置顶: 1-是, 0-否
     */
    private Integer isTop;

    /**
     * 文件内排序
     */
    private Integer sortOrder;

    /**
     * 关联源会议ID (用于实现“从会议纪要一键转笔记”)
     */
    private Long sourceMeetingId;

    /**
     * 自动填充：插入时生成
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 自动填充：插入和更新时都会自动刷新
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}