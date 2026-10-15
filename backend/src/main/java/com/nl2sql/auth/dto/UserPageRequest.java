package com.nl2sql.auth.dto;

import lombok.Data;

/** 列表分页查询参数 */
@Data
public class UserPageRequest {
    private String keyword;   // 模糊匹配 username / nickname
    private int page = 1;
    private int size = 20;
}
