package com.nl2sql.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SkillTemplateRequest {
    private Long id;
    @NotBlank
    private String name;
    @NotBlank
    private String content;
    private String description;
    private Boolean isDefault = false;
    private Boolean enabled = true;
}
