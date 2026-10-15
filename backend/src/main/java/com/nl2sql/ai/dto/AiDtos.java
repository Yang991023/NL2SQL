package com.nl2sql.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AiDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatMessage {
        private String role;
        private String content;
        /** DeepSeek/推理模型将思考过程写在此字段，content 可能为空 */
        @JsonProperty("reasoning_content")
        private String reasoningContent;

        /** 两参构造器（兼容已有调用） */
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature = 0.1;
        @JsonProperty("max_tokens")
        private Integer maxTokens = 2048;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private String id;
        private String model;
        private List<Choice> choices;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private Integer index;
            private ChatMessage message;
        }
    }
}
