package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nwzb.meeting_backend.entity.SysTopicLibrary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTopicLibraryMapper extends BaseMapper<SysTopicLibrary> {
    // 这里暂时不需要写 XML，MyBatis-Plus 自带的 selectList 足够用了
}