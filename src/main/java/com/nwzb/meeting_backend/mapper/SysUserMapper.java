package com.nwzb.meeting_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.model.vo.UserAdminVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 继承 BaseMapper 后，你已经拥有了标准的 CRUD 能力
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    Page<UserAdminVO> selectUserWithStatsPage(Page<UserAdminVO> page,
                                              @Param("username") String username,
                                              @Param("orderBy") String orderBy,
                                              @Param("isAsc") boolean isAsc);
}