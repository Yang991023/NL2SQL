package com.nl2sql.export;

import com.nl2sql.common.result.R;
import com.nl2sql.export.dto.ExportRequest;
import com.nl2sql.export.dto.TableStructureExportRequest;
import com.nl2sql.export.service.ExportService;
import com.nl2sql.export.service.TableStructureExportService;
import com.nl2sql.sql.dto.QueryResult;
import com.nl2sql.sql.service.SqlExecutorService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final TableStructureExportService tableStructureService;
    private final SqlExecutorService sqlExecutorService;

    /** 导出查询结果: 先执行 SQL, 再导出 */
    @PostMapping("/query-result")
    public void queryResult(@RequestParam Long dataSourceId,
                             @RequestBody String sql,
                             @RequestParam(required = false) String format,
                             HttpServletResponse response) {
        QueryResult result = sqlExecutorService.execute(dataSourceId, sql);
        ExportRequest req = new ExportRequest();
        req.setFormat(format == null ? "xlsx" : format);
        exportService.exportQueryResult(result, req, response);
    }

    /** 导出表结构 */
    @PostMapping("/table-structure")
    public void tableStructure(@RequestParam Long dataSourceId,
                                @RequestBody TableStructureExportRequest req,
                                HttpServletResponse response) {
        tableStructureService.exportTableStructure(dataSourceId, req, response);
    }
}
