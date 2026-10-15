package com.nl2sql.ai.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.ai.dto.AiDtos.*;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.model.entity.LlmConfig;
import com.nl2sql.model.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 客户端 - 调用 OpenAI 兼容 Chat Completion 接口
 * 兼容 Ollama / vLLM / 通义千问 / DeepSeek / 等所有 OpenAI 协议实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiClient {

    private final LlmConfigService llmConfigService;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 调用指定模型,返回 SQL 文本(自动剥离 markdown 围栏)
     */
    public String chatWith(Long llmConfigId, String systemPrompt, String userPrompt) {
        LlmConfig cfg = llmConfigService.getById(llmConfigId);
        return chatWith(cfg, systemPrompt, userPrompt);
    }

    /**
     * 调用默认模型,返回 SQL 文本(自动剥离 markdown 围栏)
     */
    public String chat(String systemPrompt, String userPrompt) {
        LlmConfig cfg = llmConfigService.getDefault();
        return chatWith(cfg, systemPrompt, userPrompt);
    }

    public String chatWith(LlmConfig cfg, String systemPrompt, String userPrompt) {
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new BizException("未配置模型 base_url");
        }
        // Mock 协议 - 离线返回示例 SQL
        if (cfg.getBaseUrl().startsWith("mock://")) {
            return mockGenerate(userPrompt);
        }
        String base = cfg.getBaseUrl().replaceAll("/+$", "");
        String url = base + "/v1/chat/completions";
        String apiKey = llmConfigService.getApiKeyPlain(cfg);

        ChatRequest req = new ChatRequest();
        req.setModel(cfg.getModel());
        req.setMessages(List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        ));
        req.setTemperature(0.1);
        req.setMaxTokens(2048);

        try {
            String body = json.writeValueAsString(req);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutS() == null ? 60 : cfg.getTimeoutS()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("AI 返回错误: status={} body={}", resp.statusCode(), resp.body());
                throw new BizException("AI 服务返回 " + resp.statusCode() + ": " + truncate(resp.body(), 200));
            }
            ChatResponse r = json.readValue(resp.body(), ChatResponse.class);
            if (r.getChoices() == null || r.getChoices().isEmpty()) {
                throw new BizException("AI 未返回内容");
            }
            String content = r.getChoices().get(0).getMessage().getContent();
            // DeepSeek/推理模型将最终答案放在 reasoning_content, content 可能为空
            if ((content == null || content.isBlank()) && r.getChoices().get(0).getMessage().getReasoningContent() != null) {
                content = r.getChoices().get(0).getMessage().getReasoningContent();
            }
            if (content == null || content.isBlank()) {
                throw new BizException("AI 未返回内容");
            }
            return stripCodeFence(content);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            throw new BizException("AI 调用失败: " + e.getMessage());
        }
    }

    private String stripCodeFence(String s) {
        if (s == null) return "";
        String t = s.trim();
        // 去除 ```sql ... ```
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }

    /**
     * Mock 生成: 根据 prompt 里的表名/字段名拼一条 SELECT
     * 真实环境接上 LLM 后会走 chatWith 主流程
     */
    private String mockGenerate(String userPrompt) {
        // 简单解析: 找 prompt 里的 "涉及的表" 区段
        String tablesSection = extract(userPrompt, "# 涉及的表", "# 表结构");
        String userQuery = extract(userPrompt, "# 用户原始需求", "# 输出要求");

        java.util.List<String> tables = new java.util.ArrayList<>();
        for (String line : tablesSection.split("\n")) {
            line = line.trim();
            if (line.startsWith("- ")) tables.add(line.substring(2).trim());
        }
        if (tables.isEmpty()) {
            return "SELECT 1; -- Mock: 未识别到表";
        }
        // 简单生成 SELECT * FROM t1 [JOIN t2] LIMIT 1000
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT *");
        sb.append("\nFROM ").append(tables.get(0));
        if (tables.size() > 1) {
            for (int i = 1; i < tables.size(); i++) {
                sb.append("\nLEFT JOIN ").append(tables.get(i))
                  .append(" ON 1=1");
            }
        }
        if (userQuery != null && !userQuery.isBlank()) {
            sb.append("\n-- 用户需求: ").append(userQuery.replace("\n", " "));
        }
        sb.append("\nLIMIT 1000;");
        return sb.toString();
    }

    private String extract(String text, String startMarker, String endMarker) {
        if (text == null) return "";
        int i = text.indexOf(startMarker);
        if (i < 0) return "";
        i += startMarker.length();
        int j = text.indexOf(endMarker, i);
        if (j < 0) j = text.length();
        return text.substring(i, j).trim();
    }
}
