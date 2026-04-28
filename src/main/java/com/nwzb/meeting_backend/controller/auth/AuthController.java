package com.nwzb.meeting_backend.controller.auth;

import com.nwzb.meeting_backend.common.Result;
import com.nwzb.meeting_backend.model.dto.LoginDTO;
import com.nwzb.meeting_backend.model.vo.UserVO;
import com.nwzb.meeting_backend.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器：处理登录与注册
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<UserVO> login(@Validated @RequestBody LoginDTO loginDTO) {
        UserVO userVO = sysUserService.login(loginDTO);
        return Result.success(userVO);
    }

    /**
     * 用户注册 (复用 LoginDTO 的字段)
     */
    @PostMapping("/register")
    public Result<String> register(@Validated @RequestBody LoginDTO loginDTO) {
        sysUserService.register(loginDTO);
        return Result.success("注册成功");
    }
}