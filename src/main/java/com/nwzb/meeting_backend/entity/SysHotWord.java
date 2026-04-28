package com.nwzb.meeting_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_hot_word")
public class SysHotWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属词库ID (如：1-默认词库, 2-医疗词库)
     */
    private Long libraryId;

    /**
     * 热词/敏感词
     */
    private String word;

    /**
     * 类型: 1-热词修正, 2-敏感词
     */
    private Integer type;

    private LocalDateTime createTime;
}