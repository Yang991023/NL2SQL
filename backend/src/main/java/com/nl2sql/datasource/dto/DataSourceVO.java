package com.nl2sql.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class DataSourceVO {
    private Long id;
    private String name;
    private String dbType;
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String remark;
    private Boolean enabled;
    private LocalDateTime lastMetaSyncAt;
    private LocalDateTime createdAt;
}
