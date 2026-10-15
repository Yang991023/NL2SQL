package com.nl2sql.sql;

import com.nl2sql.audit.service.OpLogService;
import com.nl2sql.common.result.R;
import com.nl2sql.parser.dto.CheckResult;
import com.nl2sql.parser.service.SqlSecurityService;
import com.nl2sql.security.SecurityContextUtil;
import com.nl2sql.sql.dto.QueryResult;
import com.nl2sql.sql.service.SqlExecutorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sql")
@RequiredArgsConstructor
public class SqlController {

    private final SqlSecurityService securityService;
    private final SqlExecutorService executorService;
    private final OpLogService opLog;

    /**
     * 仅校验,不执行
     */
    @PostMapping("/check")
    public R<CheckResult> check(@RequestParam Long dataSourceId, @RequestBody String sql) {
        return R.ok(securityService.checkAndFix(sql, dataSourceId));
    }

    /**
     * 执行(经过校验)
     */
    @PostMapping("/exec")
    public R<QueryResult> exec(@RequestParam Long dataSourceId, @RequestBody String sql, HttpServletRequest req) {
        try {
            QueryResult r = executorService.execute(dataSourceId, sql);
            opLog.log(SecurityContextUtil.currentUserId(), "QUERY", "dataSource", dataSourceId,
                    sql, req.getRemoteAddr(), "SUCCESS");
            return R.ok(r);
        } catch (Exception e) {
            opLog.log(SecurityContextUtil.currentUserId(), "QUERY", "dataSource", dataSourceId,
                    sql, req.getRemoteAddr(), "FAIL: " + e.getMessage());
            throw e;
        }
    }
}
