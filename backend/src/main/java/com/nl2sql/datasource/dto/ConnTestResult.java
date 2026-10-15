package com.nl2sql.datasource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnTestResult {
    private boolean success;
    private String message;
    private String dbType;     // 自动识别
    private String version;
}
