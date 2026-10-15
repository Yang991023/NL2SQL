package com.nl2sql.ai.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.model.entity.LlmConfig;
import com.nl2sql.model.service.LlmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 启动时若不存在默认模型,创建一个 Mock 模型(走 mock:// 协议,本地返回示例 SQL)
 * 真实环境由用户在前端管理后台自行配置
 */
@Slf4j
@Service
public class MockAiSeeder {

    @Value("${nl2sql.ai.auto-mock:false}")
    private boolean autoMock;

    private final LlmConfigService service;

    public MockAiSeeder(LlmConfigService service) { this.service = service; }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!autoMock) return;
        if (!service.list().isEmpty()) return;
        log.info("[MockAI] 启动注入默认 Mock 模型 (auto-mock=true)");
        var req = new com.nl2sql.model.dto.LlmConfigRequest();
        req.setName("Mock (本地调试)");
        req.setBaseUrl("mock://local");
        req.setApiKey("mock-key");
        req.setModel("mock-sql-generator");
        req.setTimeoutS(5);
        req.setIsDefault(true);
        req.setEnabled(true);
        service.save(req);
    }
}
