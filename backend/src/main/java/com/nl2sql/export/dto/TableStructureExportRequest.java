package com.nl2sql.export.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TableStructureExportRequest {
    private List<String> tables; // 格式 ["dbName.tableName"]
    private String format = "xlsx";
}
