package com.nwzb.meeting_backend.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nwzb.meeting_backend.common.CustomException;
import com.nwzb.meeting_backend.common.utils.JwtUtils;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.mapper.SysUserMapper;
import com.nwzb.meeting_backend.model.dto.LoginDTO;
import com.nwzb.meeting_backend.model.vo.UserAdminVO;
import com.nwzb.meeting_backend.model.vo.UserVO;
import com.nwzb.meeting_backend.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public UserVO login(LoginDTO loginDTO) {
        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginDTO.getUsername()));

        if (user == null) {
            throw new CustomException("用户不存在");
        }

        // 拦截封禁用户 (role = 0)
        // 放在密码校验之前，可以避免被封禁的账号恶意爆破密码耗费 CPU 算力
        if (user.getRole() != null && user.getRole() == 0) {
            throw new CustomException(403, "您的账号已被封禁，请联系管理员！");
        }

        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new CustomException("密码错误");
        }

        String token = jwtUtils.createToken(user.getId(), user.getRole());

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .token(token)
                .build();
    }

    @Override
    public void register(LoginDTO loginDTO) {
        long count = this.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginDTO.getUsername()));
        if (count > 0) {
            throw new CustomException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(loginDTO.getUsername());
        user.setPassword(BCrypt.hashpw(loginDTO.getPassword()));
        user.setRole(1);
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + user.getUsername());

        this.save(user);
    }


    @Override
    public Page<UserAdminVO> getUserPage(int pageNum, int pageSize, String username, String orderBy, boolean isAsc) {
        Page<UserAdminVO> page = new Page<>(pageNum, pageSize);

        // 安全核心：字段白名单映射，防止 SQL 注入
        String sqlOrderBy = null;
        if (StringUtils.hasText(orderBy)) {
            switch (orderBy) {
                case "id": sqlOrderBy = "u.id"; break;
                case "username": sqlOrderBy = "u.username"; break;
                case "role": sqlOrderBy = "u.role"; break;
                case "createTime": sqlOrderBy = "u.create_time"; break;
                case "totalMeetingCount": sqlOrderBy = "totalMeetingCount"; break;
                case "weekMeetingCount": sqlOrderBy = "weekMeetingCount"; break;
                case "totalAudioDuration": sqlOrderBy = "totalAudioDuration"; break;
                case "weekAudioDuration": sqlOrderBy = "weekAudioDuration"; break;
                case "totalAiDuration": sqlOrderBy = "totalAiDuration"; break;
                case "weekAiDuration": sqlOrderBy = "weekAiDuration"; break;
                default: sqlOrderBy = "u.create_time"; break; // 默认防爆
            }
        }
        return this.baseMapper.selectUserWithStatsPage(page, username, sqlOrderBy, isAsc);
    }

    @Override
    public void resetPassword(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (user.getRole() == 9) {
            throw new CustomException(403, "禁止重置超级管理员密码");
        }
        // 默认密码重置为 123456，使用 BCrypt 加密
        String defaultHash = BCrypt.hashpw("123456", BCrypt.gensalt());
        user.setPassword(defaultHash);
        this.updateById(user);
    }

    @Override
    public void updateUserRole(Long userId, Integer role) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (user.getRole() == 9) {
            throw new CustomException(403, "禁止修改超级管理员的权限");
        }
        user.setRole(role);
        this.updateById(user);
    }

    @Override
    public void toggleBan(Long userId, boolean isBan) {
        // 防止“自杀”
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (userId.equals(currentUserId)) {
            throw new CustomException(400, "非法操作：您不能封禁自己！");
        }

        SysUser user = this.getById(userId);
        if (user == null) {
            throw new CustomException(404, "用户不存在");
        }
        if (user.getRole() == 9) {
            throw new CustomException(403, "禁止封禁超级管理员");
        }
        // 如果是封禁设为 0，如果是解封则默认恢复为普通用户 1
        user.setRole(isBan ? 0 : 1);
        this.updateById(user);
    }
}