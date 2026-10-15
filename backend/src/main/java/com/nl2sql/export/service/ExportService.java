package com.nl2sql.export.service;

import com.alibaba.excel.EasyExcel;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.export.dto.ExportRequest;
import com.nl2sql.sql.dto.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ExportService {

    @Value("${nl2sql.sql.export-max-rows:100000}")
    private int exportMaxRows;

    @Value("${nl2sql.sql.export-timeout-seconds:30}")
    private int exportTimeoutSeconds;

    /**
     * 导出查询结果 (流式)
     */
    public void exportQueryResult(QueryResult data, ExportRequest req, HttpServletResponse response) {
        if (data == null) throw new BizException("无数据可导出");
        if (data.getRowCount() > exportMaxRows) {
            throw new BizException("导出数据超过 " + exportMaxRows + " 行限制");
        }
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> f = pool.submit(() -> doExport(data, req, response));
        try {
            f.get(exportTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new BizException("导出超时(" + exportTimeoutSeconds + "s)");
        } catch (Exception e) {
            throw new BizException("导出失败: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    private void doExport(QueryResult data, ExportRequest req, HttpServletResponse response) {
        String format = (req.getFormat() == null || req.getFormat().isBlank()) ? "xlsx" : req.getFormat().toLowerCase();
        String filename = buildFilename(req.getFilename(), format);

        try {
            response.setContentType("xlsx".equals(format)
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);

            if ("xlsx".equals(format)) {
                List<List<Object>> rows = new ArrayList<>();
                for (Map<String, Object> row : data.getRows()) {
                    List<Object> r = new ArrayList<>();
                    for (String c : data.getColumns()) r.add(row.get(c));
                    rows.add(r);
                }
                List<List<String>> head = new ArrayList<>();
                for (String c : data.getColumns()) head.add(List.of(c));
                EasyExcel.write(response.getOutputStream())
                        .head(head)
                        .sheet("查询结果")
                        .doWrite(rows);
            } else {
                // CSV
                StringBuilder sb = new StringBuilder();
                sb.append(String.join(",", data.getColumns())).append("\n");
                for (Map<String, Object> row : data.getRows()) {
                    List<String> vals = new ArrayList<>();
                    for (String c : data.getColumns()) {
                        Object v = row.get(c);
                        vals.add(v == null ? "" : escapeCsv(v.toString()));
                    }
                    sb.append(String.join(",", vals)).append("\n");
                }
                response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("导出失败", e);
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    private String buildFilename(String custom, String format) {
        String name = (custom == null || custom.isBlank())
                ? "nl2sql_query_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                : custom;
        return URLEncoder.encode(name + "." + format, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
