package com.nwzb.meeting_backend.config;

import com.nwzb.meeting_backend.common.utils.JwtUtils;
import com.nwzb.meeting_backend.common.utils.SecurityUtils;
import com.nwzb.meeting_backend.entity.SysUser;
import com.nwzb.meeting_backend.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    // 注入 Mapper 用于实时查询状态
    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Claims claims = jwtUtils.parseToken(token);
        if (claims != null) {
            // 解析出 userId 和 role
            Long userId = Long.valueOf(claims.get("userId").toString());

            // 实时查库，判断是否被封禁
            SysUser currentUser = sysUserMapper.selectById(userId);
            if (currentUser == null || (currentUser.getRole() != null && currentUser.getRole() == 0)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"您的账号已被封禁，请联系管理员！\",\"data\":null}");
                return false;
            }

            // 拿到数据库里最新的 role，防止 JWT 里的角色信息过期
            Integer latestRole = currentUser.getRole();

            // 1. 存入 ThreadLocal (SecurityUtils)
            SecurityUtils.setUserId(userId);
            SecurityUtils.setUserRole(latestRole);

            // 2. 存入 request 属性中 (对齐 SecurityUtils 里的取值键名)
            request.setAttribute("userId", userId);
            request.setAttribute("role", latestRole);
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"Token已失效或不存在\",\"data\":null}");
        return false;
    }

    /**
     * 请求处理完毕后执行，必须清理 ThreadLocal，防止内存泄漏和串号
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        SecurityUtils.clear();
    }
}
