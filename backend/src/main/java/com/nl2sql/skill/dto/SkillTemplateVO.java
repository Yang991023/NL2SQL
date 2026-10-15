package com.nl2sql.skill.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SkillTemplateVO {
    private Long id;
    private String name;
    private String content;
    private String description;
    private Boolean isDefault;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
