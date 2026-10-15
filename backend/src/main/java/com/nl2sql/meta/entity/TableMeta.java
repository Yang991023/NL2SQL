package com.nl2sql.meta.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 表结构元数据 - 字段详情以 JSON 存储(跨方言统一)
 * <p>
 * 注意: data_source_id + database_name + schema + table_name 唯一
 *   - MySQL: schema 为空字符串 (MySQL 的 schema ≡ database)
 *   - PostgreSQL/Oracle: schema 为 namespace/owner 名
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_table_meta",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"data_source_id", "database_name", "schema_name", "table_name"}))
public class TableMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    /** schema/namespace 名(MySQL 时为空串) */
    @Column(name = "schema_name", length = 100)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    @Column(name = "table_comment", length = 1000)
    private String tableComment;

    /**
     * 字段数组: [{name,type,length,nullable,isPk,comment,ordinal}]
     */
    @Column(columnDefinition = "TEXT")
    private String columnsJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
