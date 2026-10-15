package com.nl2sql.ratelimit;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单限流拦截器 (使用内存 ConcurrentHashMap,无外部依赖)
 * 单用户每分钟最多 N 次 SQL 执行
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, AtomicInteger> counter = new ConcurrentHashMap<>();

    @Value("${nl2sql.ratelimit.sql-exec-per-minute:30}")
    private int limit;

    @Override
    public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.contains("/sql/exec") && !path.contains("/workspace/generate-sql")) {
            return true;
        }
        Long uid = SecurityContextUtil.currentUserId();
        if (uid == null) return true;
        long minute = System.currentTimeMillis() / 60_000;
        String key = uid + ":" + minute;
        // 每分钟清理一次旧 key
        if (Math.random() < 0.01) {
            counter.keySet().removeIf(k -> {
                String[] parts = k.split(":");
                long m = Long.parseLong(parts[parts.length - 1]);
                return m < minute - 1;
            });
        }
        AtomicInteger c = counter.computeIfAbsent(key, k -> new AtomicInteger());
        int now = c.incrementAndGet();
        if (now > limit) {
            throw new BizException(429, "请求过于频繁,请稍后再试(" + limit + "次/分钟)");
        }
        return true;
    }
}
