package com.nl2sql.field.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FieldBizMetaVO {
    private Long id;
    private Long dataSourceId;
    private String databaseName;
    private String tableName;
    private String columnName;
    private String bizDesc;
    private String filterLogic;
    private String filterValue;
    private String filterLogic2;
    private String filterValue2;
}
