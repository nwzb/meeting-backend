package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nwzb.meeting_backend.entity.BizMeetingContent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizMeetingContentMapper extends BaseMapper<BizMeetingContent> {
    // 基础的 CRUD 已经够用了
}