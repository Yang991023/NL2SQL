package com.nl2sql.meta.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据库(库) 视图
 */
@Data
@NoArgsConstructor
public class DatabaseVO {
    private Long id;
    private Long dataSourceId;
    private String name;
    private String remark;
    private LocalDateTime syncedAt;
}
