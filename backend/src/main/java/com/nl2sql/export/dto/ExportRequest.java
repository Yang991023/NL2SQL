package com.nl2sql.export.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportRequest {

    @NotBlank
    private String format;     // xlsx / csv

    private String filename;    // 自定义文件名,可选
}
