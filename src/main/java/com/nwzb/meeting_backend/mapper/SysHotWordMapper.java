package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nwzb.meeting_backend.entity.SysHotWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysHotWordMapper extends BaseMapper<SysHotWord> {

    // 全量查询 (用于发给 Python )
    @Select("SELECT word FROM sys_hot_word WHERE library_id = #{libraryId} AND type = 1")
    List<String> selectAllHotWords(@Param("libraryId") Long libraryId);

    // 全量查询敏感词 (用于传给前端进行红框高亮和计数)
    @Select("SELECT word FROM sys_hot_word WHERE library_id = #{libraryId} AND type = 2")
    List<String> selectAllSensitiveWords(@Param("libraryId") Long libraryId);
}