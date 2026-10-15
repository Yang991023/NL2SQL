package com.nl2sql.canvas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CanvasSaveRequest {
    private Long id;
    @NotNull
    private Long dataSourceId;
    private String databaseName;
    @NotBlank
    private String name;
    /** JSON 字符串 */
    private String canvasContent;
}
