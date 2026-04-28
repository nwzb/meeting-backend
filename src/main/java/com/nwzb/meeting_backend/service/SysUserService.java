package com.nwzb.meeting_backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.model.dto.LoginDTO;
import com.nwzb.meeting_backend.model.vo.UserAdminVO;
import com.nwzb.meeting_backend.model.vo.UserVO;

/**
 * 用户服务类接口
 * 继承 IService 以获得 MyBatis-Plus 的基础 CRUD 能力
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户登录逻辑
     * @param loginDTO 包含用户名和密码
     * @return 返回脱敏后的用户信息及 JWT Token
     */
    UserVO login(LoginDTO loginDTO);

    /**
     * 用户注册逻辑
     * @param loginDTO 包含用户名和密码
     */
    void register(LoginDTO loginDTO);

    // 获取用户分页列表 (支持按用户名模糊搜索)
    Page<UserAdminVO> getUserPage(int pageNum, int pageSize, String username, String orderBy, boolean isAsc);

    // 重置用户密码为默认密码 (如 123456)
    void resetPassword(Long userId);

    // 更改用户角色 (升权/降权，仅超管可用)
    void updateUserRole(Long userId, Integer role);

    // 切换封禁状态 (isBan = true 为封禁，false 为解封)
    void toggleBan(Long userId, boolean isBan);
}