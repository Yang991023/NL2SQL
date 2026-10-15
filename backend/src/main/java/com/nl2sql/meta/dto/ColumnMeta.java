package com.nl2sql.meta.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表字段元信息 - 通用 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMeta {
    /** 字段名 */
    private String name;
    /** 数据类型 (原始,如 varchar(255)) */
    private String type;
    /** 长度(可空) */
    private Integer length;
    /** 是否可空 */
    private Boolean nullable;
    /** 是否主键 */
    private Boolean isPk;
    /** 数据库原始注释 */
    private String comment;
    /** 字段序号 */
    private Integer ordinal;
}
