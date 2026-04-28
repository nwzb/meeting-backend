package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nwzb.meeting_backend.entity.BizNote;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BizNoteMapper extends BaseMapper<BizNote> {
    List<GlobalSearchVO> searchGlobal(@Param("userId") Long userId, @Param("keyword") String keyword);
}