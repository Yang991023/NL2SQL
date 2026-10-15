package com.nl2sql.meta.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 数据库 Schema/库 维度元数据
 * 注意: data_source_id + database_name 唯一
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_database", schema = "sys_",
        uniqueConstraints = @UniqueConstraint(columnNames = {"data_source_id", "name"}))
public class DatabaseMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
