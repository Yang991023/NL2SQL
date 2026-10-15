package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmConfigRequest {
    private Long id;
    @NotBlank
    private String name;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer timeoutS = 60;
    private Boolean enabled = true;
    private Boolean isDefault = false;
}
