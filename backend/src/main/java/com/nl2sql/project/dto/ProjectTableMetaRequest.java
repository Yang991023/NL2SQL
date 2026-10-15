package com.nl2sql.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 新增/编辑项目表元数据的请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTableMetaRequest {

    /** 编辑时传入，新增时为 null */
    private Long id;

    /** 项目编号 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 表名称 */
    private String tableName;

    /** 字段列表 */
    private List<FieldItemRequest> fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldItemRequest {
        private Long id;          // 编辑时字段ID，新增为 null
        private String columnName; // 字段名（必填）
        private String columnType; // 字段类型
        private String columnComment; // 注释
        private String bizDesc;   // 业务描述
    }
}
