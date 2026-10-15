package com.nl2sql.datasource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对前端暴露的同步结果 wrapper.
 * 内部使用 meta.MetaSyncService.SyncResult, 此处统一加上耗时 / 错误信息.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultVO {
    /** 新增 / 更新的表数量 */
    private Integer tableCount;
    /** 新增 / 更新的字段数量 */
    private Integer columnCount;
    /** 同步耗时, 毫秒 */
    private Long durationMs;
    /** 同步的主 schema / database */
    private String schemaName;
    /** 错误信息, null 表示成功 */
    private String errorMessage;
}
