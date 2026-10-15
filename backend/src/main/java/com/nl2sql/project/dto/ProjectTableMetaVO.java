package com.nl2sql.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目-表元数据列表/详情视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTableMetaVO {

    private Long id;
    private String projectCode;
    private String projectName;
    private String tableName;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 字段列表 */
    private List<FieldItemVO> fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldItemVO {
        private Long id;
        private String columnName;   // 字段
        private String columnType;   // 字段类型
        private String columnComment;// 注释
        private String bizDesc;      // 业务描述
    }
}
