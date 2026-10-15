package com.nl2sql.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSourceRequest {

    private Long id;

    @NotBlank
    private String name;

    private String dbType;

    @NotBlank
    private String host;

    @NotBlank
    private String port;

    private String databaseName;

    @NotBlank
    private String username;

    private String password;

    private String remark;

    private Boolean enabled = true;
}
