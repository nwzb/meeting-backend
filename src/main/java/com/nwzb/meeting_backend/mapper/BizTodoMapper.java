package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nwzb.meeting_backend.entity.BizTodo;
import com.nwzb.meeting_backend.model.vo.GlobalSearchVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface BizTodoMapper extends BaseMapper<BizTodo> {
    List<GlobalSearchVO> searchGlobal(@Param("userId") Long userId, @Param("keyword") String keyword);
}