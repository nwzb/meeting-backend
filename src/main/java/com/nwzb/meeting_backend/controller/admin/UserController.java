package com.nwzb.meeting_backend.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.model.vo.UserAdminVO;
import com.nwzb.meeting_backend.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    /**
     * 获取用户列表（运维、超管均可访问）
     */
    @GetMapping("/list")
    public Result<Page<UserAdminVO>> listUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String orderBy,
            @RequestParam(defaultValue = "false") boolean isAsc) {

        Integer currentRole = SecurityUtils.getCurrentUserRole();
        if (currentRole == null || currentRole == 1) {
            return Result.error("403, 无权访问");
        }
        return Result.success(sysUserService.getUserPage(pageNum, pageSize, username, orderBy, isAsc));
    }

    /**
     * 重置密码为 123456（运维、超管均可操作）
     */
    @PutMapping("/reset-pwd/{id}")
    public Result<String> resetPassword(@PathVariable Long id) {
        Integer currentRole = SecurityUtils.getCurrentUserRole();
        if (currentRole == null || currentRole == 1) {
            return Result.error("403,无权操作");
        }
        sysUserService.resetPassword(id);
        return Result.success("密码已重置为 123456");
    }

    /**
     * 修改角色/升权（★ 仅超管 role=9 可以操作）
     */
    @PutMapping("/role/{id}")
    public Result<String> updateRole(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer currentRole = SecurityUtils.getCurrentUserRole();
        // 核心拦截：非超管直接踢回
        if (currentRole == null || currentRole != 9) {
            return Result.error("403,权限不足：仅超级管理员可调整角色");
        }
        Integer newRole = body.get("role");
        if (newRole == null || (newRole != 1 && newRole != 2 && newRole != 3)) {
            return Result.error("400,非法的角色参数");
        }
        sysUserService.updateUserRole(id, newRole);
        return Result.success("角色修改成功");
    }

    /**
     * 封禁/解封用户（运维、超管均可操作）
     */
    @PutMapping("/ban/{id}")
    public Result<String> toggleBan(@PathVariable Long id, @RequestParam boolean isBan) {
        Integer currentRole = SecurityUtils.getCurrentUserRole();
        // 拦截：普通用户(1)和审计管理(3)无权操作
        if (currentRole == null || currentRole == 1 || currentRole == 3) {
            return Result.error("403, 无权操作");
        }
        sysUserService.toggleBan(id, isBan);
        return Result.success(isBan ? "账号已封禁" : "账号已解封");
    }
}