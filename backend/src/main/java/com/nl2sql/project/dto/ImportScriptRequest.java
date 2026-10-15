package com.nl2sql.project.dto;

import lombok.Data;

/** 导入脚本请求体 */
@Data
public class ImportScriptRequest {
    /** 项目编号 */
    private String projectCode;
    /** 项目名称 */
    private String projectName;
    /** 表结构脚本 */
    private String script;
    /** 识别模式：ddl = 建表语句；list = 表结构清单 */
    private String mode = "ddl";
}
