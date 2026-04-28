package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_topic_library")
public class SysTopicLibrary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 词库名称 (如: 互联网, 金融, 法律)
     */
    private String name;

    /**
     * 词库描述
     */
    private String description;

    /**
     * 是否公开: 1-是, 0-仅管理员可见
     */
    private Integer isPublic;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}