package com.nl2sql.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_query_canvas", schema = "sys_")
public class QueryCanvas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "database_name", length = 100)
    private String databaseName;

    @Column(nullable = false, length = 100)
    private String name;

    /** 画布完整状态 JSON */
    @Column(name = "canvas_content", columnDefinition = "TEXT")
    private String canvasContent;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
