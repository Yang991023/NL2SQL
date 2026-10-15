package com.nl2sql.field.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 字段业务元数据 - 用户对某表某字段的自定义描述/查询逻辑/默认值
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_field_biz_meta", schema = "sys_",
        uniqueConstraints = @UniqueConstraint(columnNames = {"data_source_id", "database_name", "table_name", "column_name"}))
public class FieldBizMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 200)
    private String columnName;

    @Column(name = "biz_desc", length = 1000)
    private String bizDesc;

    /** NULL / IN / RANGE / EQUAL / LIKE / NE / RANGE_AND / RANGE_OR */
    @Column(name = "filter_logic", length = 30)
    private String filterLogic;

    @Column(name = "filter_value", length = 1000)
    private String filterValue;

    /** 第二段范围操作符 (如 >=, <=) */
    @Column(name = "filter_logic2", length = 30)
    private String filterLogic2;

    /** 第二段范围值 */
    @Column(name = "filter_value2", length = 1000)
    private String filterValue2;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
