package com.nl2sql.sql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private int rowCount;
    private long costMs;
    private boolean truncated;
}
