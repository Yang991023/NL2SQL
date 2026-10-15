package com.nl2sql.meta.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表详情视图 - 数据库原生结构信息(不含用户自定义业务元数据)
 */
@Data
@NoArgsConstructor
public class TableMetaVO {
    private Long dataSourceId;
    private String databaseName;
    /** schema/namespace 名(MySQL 时为 null) */
    private String schemaName;
    private String tableName;
    private String tableComment;
    private List<ColumnMeta> columns;
    private LocalDateTime syncedAt;
}
