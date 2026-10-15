package com.nl2sql.datasource.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_data_source", schema = "sys_")
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** MYSQL / POSTGRES / ORACLE */
    @Column(name = "db_type", length = 20)
    private String dbType;

    @Column(nullable = false, length = 200)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "database_name", length = 100)
    private String databaseName;

    @Column(nullable = false, length = 100)
    private String username;

    /** AES 加密后的密码 */
    @Column(name = "password_enc", columnDefinition = "TEXT")
    private String passwordEnc;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_meta_sync_at")
    private LocalDateTime lastMetaSyncAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;
}
