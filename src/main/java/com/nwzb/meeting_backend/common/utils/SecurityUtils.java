package com.nwzb.meeting_backend.common.utils;

import com.nwzb.meeting_backend.common.CustomException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 安全工具类
 */
public class SecurityUtils {
    // 使用 ThreadLocal 存储当前线程的用户 ID，保证并发安全
    private static final ThreadLocal<Long> USER_ID_THREAD_LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<Integer> USER_ROLE_THREAD_LOCAL = new ThreadLocal<>();

    // 存入 userId (在你的 JwtInterceptor 验证 token 成功后调用这个方法)
    public static void setUserId(Long userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    // 存入 role (在你的 JwtInterceptor 验证 token 成功后调用这个方法)
    public static void setUserRole(Integer role) {
        USER_ROLE_THREAD_LOCAL.set(role);
    }

    // 获取 userId (Controller 层直接调用这个方法)
    public static Long getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    // 获取 role
    public static Integer getUserRole() {
        return USER_ROLE_THREAD_LOCAL.get();
    }

    // 清除上下文 (在拦截器的 afterCompletion 中调用，防止内存泄漏)
    public static void clear() {
        USER_ID_THREAD_LOCAL.remove();
        USER_ROLE_THREAD_LOCAL.remove(); // 新增：同步清除 Role，防止线程池复用导致的越权
    }

    /**
     * 获取当前登录用户ID
     * 注意：需配合拦截器在请求头/属性中存入 userId
     */
    public static Long getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // 假设你的 JwtInterceptor 将解析出的 userId 存到了 request 属性中
            Object userId = request.getAttribute("userId");
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
        }
        // 兜底：尝试从 ThreadLocal 获取
        Long threadLocalId = getUserId();
        if (threadLocalId != null) {
            return threadLocalId;
        }

        // 严格阻断未授权请求，直接抛出 401 异常，防止数据越权和污染
        throw new CustomException(401, "凭证已过期或未携带有效Token，请重新登录");
    }

    /**
     * 获取当前登录用户角色（权限）
     * 注意：需配合拦截器在请求头/属性/ThreadLocal中存入 role
     */
    public static Integer getCurrentUserRole() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            // 假设 JwtInterceptor 解析 Token 后将 role 存到了 request 属性中
            Object role = request.getAttribute("role");
            if (role != null) {
                return Integer.valueOf(role.toString());
            }
        }
        // 兜底：尝试从 ThreadLocal 获取
        Integer threadLocalRole = getUserRole();
        if (threadLocalRole != null) {
            return threadLocalRole;
        }
        // 默认返回普通用户角色 1 (最小权限原则，防止未授权访问)
        return 1;
    }
}
