package com.nl2sql.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 项目表的字段配置
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_project_table_field", schema = "sys_",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"project_table_id", "column_name"}
        ))
public class ProjectTableField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目表记录ID */
    @Column(name = "project_table_id", nullable = false)
    private Long projectTableId;

    /** 字段名（必填） */
    @Column(name = "column_name", nullable = false, length = 200)
    private String columnName;

    /** 字段类型 */
    @Column(name = "column_type", length = 100)
    private String columnType;

    /** 字段注释 */
    @Column(name = "column_comment", length = 500)
    private String columnComment;

    /** 业务描述 */
    @Column(name = "biz_desc", length = 1000)
    private String bizDesc;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
