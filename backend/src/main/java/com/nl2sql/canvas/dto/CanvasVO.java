package com.nl2sql.canvas.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CanvasVO {
    private Long id;
    private Long dataSourceId;
    private String databaseName;
    private String name;
    private String canvasContent;
    private LocalDateTime updatedAt;
}
