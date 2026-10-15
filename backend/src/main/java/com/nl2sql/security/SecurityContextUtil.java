package com.nl2sql.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具 - 获取当前登录用户
 */
public final class SecurityContextUtil {
    private SecurityContextUtil() {}
    public static Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getPrincipal() == null) return null;
        try {
            return Long.valueOf(a.getPrincipal().toString());
        } catch (Exception e) {
            return null;
        }
    }
}
