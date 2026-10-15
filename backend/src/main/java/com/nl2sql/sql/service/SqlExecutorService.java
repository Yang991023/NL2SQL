package com.nl2sql.sql.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.parser.dto.CheckResult;
import com.nl2sql.parser.service.SqlSecurityService;
import com.nl2sql.security.AESUtil;
import com.nl2sql.sql.dto.QueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SQL 执行器 (受 SqlSecurityService 保护)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutorService {

    private final DataSourceRepository dataSourceRepo;
    private final AESUtil aes;
    private final SqlSecurityService security;

    @Value("${nl2sql.sql.query-timeout-seconds:15}")
    private int queryTimeoutSeconds;

    @Value("${nl2sql.sql.max-rows:10000}")
    private int maxRows;

    public QueryResult execute(Long dataSourceId, String sql) {
        long t0 = System.currentTimeMillis();
        // 1. 安全校验(必经之路)
        CheckResult check = security.checkAndFix(sql, dataSourceId);
        if (check.getStatus() == CheckResult.Status.REJECT) {
            throw new BizException(403, "SQL 校验失败: " + check.getReason());
        }
        String safeSql = check.getFixedSql();

        DataSource ds = dataSourceRepo.findById(dataSourceId)
                .orElseThrow(() -> new BizException("数据源不存在"));

        try (Connection conn = openConn(ds)) {
            applyStatementTimeout(conn, ds.getDbType());

            try (PreparedStatement ps = conn.prepareStatement(safeSql)) {
                ps.setQueryTimeout(queryTimeoutSeconds);
                ps.setMaxRows(maxRows);
                ps.setFetchSize(1000);

                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(md.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    int count = 0;
                    boolean truncated = false;
                    while (rs.next()) {
                        if (count >= maxRows) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                        count++;
                    }

                    long cost = System.currentTimeMillis() - t0;
                    log.info("[SQL 执行] dataSource={} cost={}ms rows={} warnings={}",
                            ds.getName(), cost, count, check.getIssues().size());
                    return new QueryResult(columns, rows, count, cost, truncated);
                }
            }
        } catch (SQLException e) {
            log.error("SQL 执行失败", e);
            throw new BizException("SQL 执行失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("SQL 执行异常", e);
            throw new BizException("SQL 执行异常: " + e.getMessage());
        }
    }

    private Connection openConn(DataSource ds) throws Exception {
        Properties props = new Properties();
        props.setProperty("user", ds.getUsername());
        props.setProperty("password", aes.decrypt(ds.getPasswordEnc()));
        props.setProperty("connectTimeout", "5000");
        String url = switch (ds.getDbType().toUpperCase()) {
            case "MYSQL" -> "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort()
                    + "/" + (ds.getDatabaseName() == null ? "" : ds.getDatabaseName())
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            case "POSTGRES", "POSTGRESQL" -> "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort()
                    + "/" + (ds.getDatabaseName() == null ? "postgres" : ds.getDatabaseName());
            case "ORACLE" -> "jdbc:oracle:thin:@" + ds.getHost() + ":" + ds.getPort() + ":"
                    + (ds.getDatabaseName() == null ? "ORCL" : ds.getDatabaseName());
            default -> throw new BizException("不支持的数据库类型: " + ds.getDbType());
        };
        return DriverManager.getConnection(url, props);
    }

    private void applyStatementTimeout(Connection conn, String dbType) throws SQLException {
        String sql = switch (dbType == null ? "" : dbType.toUpperCase()) {
            case "POSTGRES", "POSTGRESQL" -> "SET STATEMENT_TIMEOUT = " + (queryTimeoutSeconds * 1000);
            case "MYSQL" -> "SET SESSION MAX_EXECUTION_TIME = " + (queryTimeoutSeconds * 1000);
            case "ORACLE" -> "ALTER SESSION SET MAX_IDLE_TIME = " + queryTimeoutSeconds; // 仅作 demo
            default -> null;
        };
        if (sql != null) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            } catch (SQLException ignore) {
                // 某些驱动不兼容,忽略
            }
        }
    }
}
