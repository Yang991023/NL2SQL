package com.nl2sql.project.dto;

import lombok.Data;

/** 自动修复脚本请求 */
@Data
public class AutoFixRequest {
    /** 表结构脚本（可能有格式错误） */
    private String script;
    /** 识别模式：ddl = 建表语句；list = 表结构清单 */
    private String mode = "ddl";
    /** 顶部填写的项目编号（会注入到 DDL 表名声明行 @projectCode） */
    private String projectCode;
    /** 顶部填写的项目名称（会注入到 DDL 表名声明行 @projectName） */
    private String projectName;
}
