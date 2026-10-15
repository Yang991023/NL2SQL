package com.nl2sql.meta.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Schema 视图 - 用于级联选择器
 * <p>
 * MySQL: schema 与 database 相同, 此层通常不显示
 * PostgreSQL/Oracle: schema 是 database 下的命名空间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchemaVO {
    /** 物理数据库名 (database 层级) */
    private String database;
    /** schema 命名空间名 */
    private String name;
}
