package com.nl2sql.audit.service;

import com.nl2sql.audit.entity.OpLog;
import com.nl2sql.audit.repo.OpLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpLogService {

    private final OpLogRepository repo;

    @Async
    public void log(Long userId, String action, String targetType, Long targetId, String detail, String ip, String result) {
        try {
            OpLog o = new OpLog();
            o.setUserId(userId);
            o.setAction(action);
            o.setTargetType(targetType);
            o.setTargetId(targetId);
            o.setDetail(detail == null ? null : (detail.length() > 4000 ? detail.substring(0, 4000) : detail));
            o.setIp(ip);
            o.setResult(result);
            repo.save(o);
        } catch (Exception ignore) {
            // 审计失败不阻塞主流程
        }
    }
}
