package com.nl2sql.export.service;

import com.alibaba.excel.EasyExcel;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.export.dto.TableStructureExportRequest;
import com.nl2sql.field.service.FieldBizMetaService;
import com.nl2sql.meta.entity.TableMeta;
import com.nl2sql.meta.repo.TableMetaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import com.nl2sql.meta.dto.ColumnMeta;
import com.nl2sql.field.dto.FieldBizMetaVO;

/**
 * 表结构导出 (含用户自定义业务元数据)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableStructureExportService {

    private final TableMetaRepository tableMetaRepo;
    private final FieldBizMetaService fieldBizMetaService;
    private final ObjectMapper json = new ObjectMapper();

    public void exportTableStructure(Long dataSourceId, TableStructureExportRequest req, HttpServletResponse response) {
        String format = req.getFormat() == null ? "xlsx" : req.getFormat().toLowerCase();
        String filename = URLEncoder.encode("table_structure_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "." + format, StandardCharsets.UTF_8).replace("+", "%20");

        try {
            response.setContentType("xlsx".equals(format)
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);

            // 收集所有表的结构
            List<List<String>> head = List.of(
                    List.of("数据库", "表名", "表注释", "字段名", "类型", "主键", "可空", "原始注释", "业务描述", "查询逻辑", "默认值"));
            List<List<Object>> rows = new ArrayList<>();
            for (String t : req.getTables()) {
                String[] parts = t.split("\\.");
                String db = parts.length > 1 ? parts[0] : "";
                String tb = parts.length > 1 ? parts[1] : parts[0];
                TableMeta tm = tableMetaRepo
                        .findByDataSourceIdAndDatabaseNameAndTableName(dataSourceId, db, tb).orElse(null);
                if (tm == null) {
                    rows.add(List.of(db, tb, "(未同步)", "-", "-", "-", "-", "-", "-", "-", "-"));
                    continue;
                }
                List<ColumnMeta> cols = parse(tm.getColumnsJson());
                List<FieldBizMetaVO> bizMetas = fieldBizMetaService.list(dataSourceId, db, tb);
                Map<String, FieldBizMetaVO> bizMap = new HashMap<>();
                for (FieldBizMetaVO b : bizMetas) bizMap.put(b.getColumnName(), b);

                for (ColumnMeta c : cols) {
                    FieldBizMetaVO b = bizMap.get(c.getName());
                    rows.add(List.of(
                            db, tb, tm.getTableComment() == null ? "" : tm.getTableComment(),
                            c.getName(), c.getType(),
                            Boolean.TRUE.equals(c.getIsPk()) ? "✓" : "",
                            Boolean.TRUE.equals(c.getNullable()) ? "Y" : "N",
                            c.getComment() == null ? "" : c.getComment(),
                            b == null || b.getBizDesc() == null ? "" : b.getBizDesc(),
                            b == null || b.getFilterLogic() == null ? "" : b.getFilterLogic(),
                            b == null || b.getFilterValue() == null ? "" : b.getFilterValue()
                    ));
                }
            }

            if ("xlsx".equals(format)) {
                EasyExcel.write(response.getOutputStream())
                        .head(head)
                        .sheet("表结构")
                        .doWrite(rows);
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("数据库,表名,表注释,字段名,类型,主键,可空,原始注释,业务描述,查询逻辑,默认值\n");
                for (List<Object> r : rows) {
                    List<String> vs = new ArrayList<>();
                    for (Object o : r) vs.add(o == null ? "" : escape(o.toString()));
                    sb.append(String.join(",", vs)).append("\n");
                }
                response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("导出表结构失败", e);
            throw new BizException("导出表结构失败: " + e.getMessage());
        }
    }

    private List<ColumnMeta> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return this.json.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
