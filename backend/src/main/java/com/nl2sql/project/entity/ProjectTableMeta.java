package com.nl2sql.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 项目-表元数据管理
 * 用户自行维护的项目信息和表结构配置（不依赖外部数据源）
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_project_table_meta", schema = "sys_")
public class ProjectTableMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目编号（不允许重复） */
    @Column(name = "project_code", nullable = false, length = 50)
    private String projectCode;

    /** 项目名称 */
    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    /** 表名称（同一项目下不允许重复） */
    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    /** 创建人用户ID */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
