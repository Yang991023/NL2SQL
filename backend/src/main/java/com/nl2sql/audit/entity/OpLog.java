package com.nl2sql.audit.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "t_op_log", schema = "sys_", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class OpLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String action;       // LOGIN / QUERY / EXPORT / DATASOURCE_SAVE / ...

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;       // SQL / Payload 等

    @Column(length = 200)
    private String ip;

    @Column(length = 20)
    private String result;       // SUCCESS / FAIL

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
