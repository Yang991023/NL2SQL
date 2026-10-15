package com.nl2sql.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class LlmConfigVO {
    private Long id;
    private String name;
    private String baseUrl;
    private String model;
    private Integer timeoutS;
    private Boolean enabled;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
