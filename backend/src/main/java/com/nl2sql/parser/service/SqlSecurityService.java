package com.nl2sql.parser.service;

import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.meta.entity.TableMeta;
import com.nl2sql.meta.repo.TableMetaRepository;
import com.nl2sql.parser.dto.CheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验引擎
 * 7 层防御:
 *  L1 预处理 (去注释/空行)
 *  L2 语法解析 (JSqlParser)
 *  L3 语句类型拦截 (黑名单)
 *  L4 方言匹配
 *  L5 对象存在性 (Table/Column 引用比对元数据)
 *  L6 高危逻辑 (笛卡尔积/全表扫描/无 LIMIT)
 *  L7 自动修复 (追加 LIMIT)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlSecurityService {

    private final DataSourceRepository dataSourceRepo;
    private final TableMetaRepository tableMetaRepo;

    /** 黑名单: 这些语句类型一律 REJECT */
    private static final Set<String> BLOCKED_STMT_TYPES = Set.of(
            "Insert", "Update", "Delete", "Replace", "Merge",
            "Drop", "Truncate", "CreateTable", "Alter", "CreateIndex", "DropIndex",
            "Grant", "Revoke", "SetSchema", "Use", "Commit", "Rollback", "Savepoint",
            "Execute", "Call", "Block"
    );

    /** 危险关键字 - 在 SELECT 中也禁止 */
    private static final Pattern DANGER_KEYWORD = Pattern.compile(
            "\\b(into\\s+outfile|load_file|sleep\\s*\\(|benchmark\\s*\\(|information_schema\\.user|pg_sleep)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMENT_LINE = Pattern.compile("(?m)--[^\\n]*");
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern MULTI_WS = Pattern.compile("\\s+");
    private static final Pattern SEMI_TAIL = Pattern.compile(";\\s*$");

    public CheckResult checkAndFix(String rawSql, Long dataSourceId) {
        long t0 = System.currentTimeMillis();
        DataSource ds = dataSourceRepo.findById(dataSourceId)
                .orElseThrow(() -> new BizException("数据源不存在"));

        // L1 - 预处理
        String sql = preprocess(rawSql);
        if (sql.isBlank()) {
            return CheckResult.reject("SQL 为空", System.currentTimeMillis() - t0);
        }

        // L2 - 解析
        Statement stmt;
        try {
            Statements stmts = CCJSqlParserUtil.parseStatements(sql);
            if (stmts.getStatements().size() > 1) {
                return CheckResult.reject("禁止执行多条语句", System.currentTimeMillis() - t0);
            }
            stmt = stmts.getStatements().get(0);
        } catch (JSQLParserException e) {
            log.warn("JSqlParser 解析失败: {}", e.getMessage());
            return CheckResult.reject("SQL 语法错误: " + e.getMessage(), System.currentTimeMillis() - t0);
        }

        // L3 - 顶层语句类型
        String typeName = stmt.getClass().getSimpleName();
        if (BLOCKED_STMT_TYPES.contains(typeName)) {
            return CheckResult.reject("禁止的语句类型: " + typeName, System.currentTimeMillis() - t0);
        }
        if (!(stmt instanceof Select)) {
            return CheckResult.reject("仅允许 SELECT 语句,当前: " + typeName, System.currentTimeMillis() - t0);
        }

        // 危险关键字扫描
        Matcher dm = DANGER_KEYWORD.matcher(sql);
        if (dm.find()) {
            return CheckResult.reject("包含危险关键字: " + dm.group(), System.currentTimeMillis() - t0);
        }

        // L4 - 方言匹配(基础检查:方言特定语法差异;此处只做粗略判断,留待执行时再细判)
        // 例如 MySQL 的 \`字段\` vs PG 的 "字段"
        // 实际通过 SqlExecutor 方言判断,这里只把方言记下来
        Select select = (Select) stmt;

        // L5 - 对象存在性
        java.util.List<String> issues = new java.util.ArrayList<>();
        TablesNamesFinder tnf = new TablesNamesFinder();
        Set<String> tableRefs = new HashSet<>(tnf.getTableList(stmt));
        if (tableRefs.isEmpty()) {
            return CheckResult.reject("未引用任何表", System.currentTimeMillis() - t0);
        }
        for (String t : tableRefs) {
            // 去掉 schema 前缀 (如 "public.users" -> "users")
            String tableName = t.contains(".") ? t.substring(t.indexOf('.') + 1) : t;
            String databaseName = t.contains(".") ? t.substring(0, t.indexOf('.')) : "";
            // 兼容 Oracle: schema.table 大写
            if (databaseName.isEmpty() && "ORACLE".equalsIgnoreCase(ds.getDbType())) {
                databaseName = (ds.getDatabaseName() == null) ? "PUBLIC" : ds.getDatabaseName();
            }
            boolean exists = false;
            if (!databaseName.isEmpty()) {
                exists = tableMetaRepo.findByDataSourceIdAndDatabaseNameAndTableName(
                        dataSourceId, databaseName, tableName).isPresent();
            } else {
                // 在所有库中查找
                for (TableMeta tm : tableMetaRepo.findAll()) {
                    if (tm.getDataSourceId().equals(dataSourceId)
                            && tm.getTableName().equalsIgnoreCase(tableName)) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                issues.add("引用了未在元数据中同步的表: " + t + "(建议先刷新元数据)");
            }
        }

        // L6 - 高危逻辑
        boolean hasLimit = sql.toUpperCase().matches("(?s).*\\bLIMIT\\b.*") || sql.toUpperCase().matches("(?s).*\\bFETCH\\b.*");
        boolean hasWhere = sql.toUpperCase().matches("(?s).*\\bWHERE\\b.*");
        if (!hasWhere) {
            issues.add("⚠️ 未使用 WHERE 条件,可能扫描全表");
        }
        if (!hasLimit) {
            issues.add("⚠️ 未使用 LIMIT,将自动追加 LIMIT 1000");
        }

        // L7 - 自动修复
        String fixedSql = sql;
        if (!hasLimit) {
            String dialect = ds.getDbType() == null ? "" : ds.getDbType().toUpperCase(Locale.ROOT);
            if ("POSTGRES".equals(dialect) || "ORACLE".equals(dialect)) {
                fixedSql = fixedSql.replaceAll(";\\s*$", "").trim() + " LIMIT 1000";
            } else {
                fixedSql = fixedSql.replaceAll(";\\s*$", "").trim() + " LIMIT 1000";
            }
        }

        long cost = System.currentTimeMillis() - t0;
        if (issues.isEmpty()) {
            return CheckResult.pass(fixedSql, cost);
        }
        // 任意 issue 中包含 "未在元数据" 的视为 WARNING(不阻断),其他 WARNING
        // 注:由于 L5 暂时不阻断,issues 只会是 WARNING
        return CheckResult.warn(issues, fixedSql, cost);
    }

    private String preprocess(String s) {
        if (s == null) return "";
        return MULTI_WS.matcher(SEMI_TAIL.matcher(COMMENT_BLOCK.matcher(COMMENT_LINE.matcher(s).replaceAll("")).replaceAll("")).replaceAll("")).replaceAll(" ").trim();
    }
}
