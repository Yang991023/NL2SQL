package com.nl2sql.meta.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.meta.dto.ColumnMeta;
import com.nl2sql.meta.dto.DatabaseVO;
import com.nl2sql.meta.dto.SchemaVO;
import com.nl2sql.meta.dto.TableMetaVO;
import com.nl2sql.meta.dialect.DbDialect;
import com.nl2sql.meta.dialect.DialectRouter;
import com.nl2sql.meta.entity.DatabaseMeta;
import com.nl2sql.meta.entity.TableMeta;
import com.nl2sql.meta.repo.DatabaseRepository;
import com.nl2sql.meta.repo.TableMetaRepository;
import com.nl2sql.security.AESUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 元数据同步服务
 * 流程: 取连接 -> 选方言 -> 列库 -> 列表 -> 列表 -> 字段 -> 批量入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaSyncService {

    private final DataSourceRepository dataSourceRepo;
    private final DatabaseRepository databaseRepo;
    private final TableMetaRepository tableMetaRepo;
    private final DialectRouter dialectRouter;
    private final AESUtil aes;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 异步同步入口 - 供数据源新增/手动刷新调用
     */
    @Async
    @Transactional
    public void syncAsync(Long dataSourceId) {
        DataSource ds = dataSourceRepo.findById(dataSourceId).orElse(null);
        if (ds == null) return;
        syncInternal(ds);
    }

    /**
     * Controller 调用入口:异步执行
     */
    @Async
    @Transactional
    public void syncInternalAsync(Long dataSourceId) {
        syncAsync(dataSourceId);
    }

    /**
     * 同步全流程 - 返回本次同步的库数 / 表数
     */
    public synchronized SyncResult syncInternal(DataSource ds) {
        log.info("[元数据同步] 开始 dataSource={} type={}", ds.getName(), ds.getDbType());
        DbDialect dialect = dialectRouter.route(ds.getDbType());

        try (Connection conn = openConn(ds)) {
            // 0. 清理该数据源下的旧表元数据 (避免 schema 隔离后产生重复记录)
            tableMetaRepo.deleteByDataSourceId(ds.getId());

            // 1. 列库
            List<String> databases = dialect.listDatabases(conn);
            log.info("[元数据同步] 发现 {} 个库", databases.size());

            int tableCount = 0;
            int columnCount = 0;
            String firstDb = databases.isEmpty() ? null : databases.get(0);
            for (String db : databases) {
                // 1.1 写 DatabaseMeta
                DatabaseMeta dm = databaseRepo.findByDataSourceIdAndName(ds.getId(), db)
                        .orElseGet(() -> {
                            DatabaseMeta x = new DatabaseMeta();
                            x.setDataSourceId(ds.getId());
                            x.setName(db);
                            return x;
                        });
                databaseRepo.save(dm);

                // 1.2 列该库下的 schema 列表，再对每个 schema 列其下的表
                // - MySQL: schema 为空，只列 db 下的表（无 namespace 概念）
                // - PostgreSQL/Oracle: 至少有一个默认 schema (如 public)，正常遍历
                List<String> schemas;
                try {
                    schemas = dialect.listSchemas(conn, db);
                } catch (Exception e) {
                    log.warn("列库 {} 的 schema 失败,降级为默认 schema: {}", db, e.getMessage());
                    schemas = List.of("public");
                }

                for (String schema : schemas.isEmpty() ? List.of("") : schemas) {
                    List<String> tables;
                    try {
                        tables = dialect.listTables(conn, db, schema.isEmpty() ? null : schema);
                    } catch (Exception e) {
                        log.warn("列库 {}/{} 的表失败,跳过: {}", db, schema, e.getMessage());
                        continue;
                    }
                    // schema 为空字符串时表示该方言不支持 schema 隔离（MySQL），此时直接用 db 作为 schema
                    final String resolvedSchema = schema.isEmpty() ? "" : schema;
                    log.info("[元数据同步] 库 {}/{} 下 {} 张表", db, resolvedSchema, tables.size());

                    // 1.3 每张表
                    for (String table : tables) {
                        try {
                            // 解析 schema.tablename 格式(后端返回的格式可能带 schema 前缀)
                            String realSchema = resolvedSchema;
                            String realTable = table;
                            if (table != null && table.contains(".")) {
                                int dot = table.indexOf('.');
                                realSchema = table.substring(0, dot);
                                realTable = table.substring(dot + 1);
                            }
                            // 构造方言期望的查询名: PG/Oracle 需要 "schema.table" 格式才能定位到正确 schema
                            // MySQL 方言内部只用 database + table, schema 前缀会被其忽略(见 MySqlDialect.describeTable)
                            String tableForDescribe = (realSchema != null && !realSchema.isBlank())
                                    ? realSchema + "." + realTable
                                    : realTable;
                            List<ColumnMeta> cols = dialect.describeTable(conn, db, tableForDescribe);
                            TableMeta tm = tableMetaRepo
                                    .findByDataSourceIdAndDatabaseNameAndSchemaNameAndTableName(
                                            ds.getId(), db, realSchema, realTable)
                                    .orElseGet(TableMeta::new);
                            tm.setDataSourceId(ds.getId());
                            tm.setDatabaseName(db);
                            tm.setSchemaName(realSchema);
                            tm.setTableName(realTable);
                            tm.setTableComment(null);
                            tm.setColumnsJson(toJson(cols));
                            tm.setSyncedAt(LocalDateTime.now());
                            tableMetaRepo.save(tm);
                            tableCount++;
                            columnCount += cols == null ? 0 : cols.size();
                        } catch (Exception e) {
                            log.warn("表 {}.{}.{} 结构拉取失败,跳过: {}", db, resolvedSchema, table, e.getMessage());
                        }
                    }
                }
            }

            // 2. 更新最后同步时间
            ds.setLastMetaSyncAt(LocalDateTime.now());
            dataSourceRepo.save(ds);
            log.info("[元数据同步] 完成 dataSource={} 共 {}/{} 列={}", ds.getName(), databases.size(), tableCount, columnCount);
            return new SyncResult(databases.size(), tableCount, columnCount, firstDb);
        } catch (Exception e) {
            log.error("[元数据同步] 失败 dataSource=" + ds.getName(), e);
            throw new BizException("元数据同步失败: " + e.getMessage());
        }
    }

    /**
     * 打开目标数据源连接
     */
    private Connection openConn(DataSource ds) throws Exception {
        Properties props = new Properties();
        props.setProperty("user", ds.getUsername());
        props.setProperty("password", aes.decrypt(ds.getPasswordEnc()));
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");
        String url = switch (ds.getDbType().toUpperCase()) {
            case "MYSQL" -> "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            case "POSTGRES", "POSTGRESQL" -> {
                // 优先用用户指定的 databaseName，否则降级到默认的 postgres
                String db = (ds.getDatabaseName() != null && !ds.getDatabaseName().isBlank())
                        ? ds.getDatabaseName()
                        : "postgres";
                yield "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort() + "/" + db;
            }
            case "ORACLE" -> "jdbc:oracle:thin:@" + ds.getHost() + ":" + ds.getPort() + "/" + (ds.getDatabaseName() == null ? "ORCL" : ds.getDatabaseName());
            default -> throw new BizException("不支持的数据库类型: " + ds.getDbType());
        };
        return DriverManager.getConnection(url, props);
    }

    private String toJson(List<ColumnMeta> cols) {
        try {
            return json.writeValueAsString(cols);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<ColumnMeta> fromJson(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        try {
            return json.readValue(s, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ========== 查询接口 ==========

    public List<DatabaseVO> listDatabases(Long dataSourceId) {
        return databaseRepo.findByDataSourceIdOrderByName(dataSourceId).stream().map(d -> {
            DatabaseVO v = new DatabaseVO();
            v.setId(d.getId());
            v.setDataSourceId(d.getDataSourceId());
            v.setName(d.getName());
            v.setRemark(d.getRemark());
            v.setSyncedAt(d.getUpdatedAt());
            return v;
        }).toList();
    }

    /**
     * 返回数据库下的 schema 列表,用于级联第三级
     * <p>
     * - MySQL: 返回空列表(前端不显示 Schema 级)
     * - PostgreSQL: 返回 namespace 列表(public/db4ai/...)
     * - Oracle: 返回空(Owner 即 schema,已经在 Database 层级处理)
     */
    public List<SchemaVO> listSchemas(Long dataSourceId, String database) {
        DataSource ds = dataSourceRepo.findById(dataSourceId).orElse(null);
        if (ds == null) return List.of();
        String dbType = ds.getDbType();
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            // MySQL: schema ≡ database, 不单独显示
            return List.of();
        }
        try (Connection conn = openConn(ds)) {
            DbDialect dialect = dialectRouter.route(dbType);
            List<String> schemas = dialect.listSchemas(conn, database);
            return schemas.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> new SchemaVO(database, s))
                    .toList();
        } catch (Exception e) {
            log.warn("[listSchemas] dataSource={} db={} 失败: {}", dataSourceId, database, e.getMessage());
            return List.of();
        }
    }

    public List<String> listTables(Long dataSourceId, String database) {
        return listTables(dataSourceId, database, null);
    }

    public List<String> listTables(Long dataSourceId, String database, String schema) {
        if (schema == null || schema.isBlank()) {
            // 兼容旧调用 - 只按 (ds, db) 查
            return tableMetaRepo.findByDataSourceIdAndDatabaseName(dataSourceId, database)
                    .stream().map(TableMeta::getTableName).sorted().toList();
        }
        return tableMetaRepo.findByDataSourceIdDatabaseAndSchema(dataSourceId, database, schema)
                .stream().map(TableMeta::getTableName).sorted().toList();
    }

    public TableMetaVO tableDetail(Long dataSourceId, String database, String table) {
        return tableDetail(dataSourceId, database, null, table);
    }

    public TableMetaVO tableDetail(Long dataSourceId, String database, String schema, String table) {
        // PostgreSQL 表名格式 "schema.tablename" 时, 自动拆分
        String realSchema = schema;
        String realTable = table;
        if (table != null && table.contains(".")) {
            int dot = table.indexOf('.');
            realSchema = table.substring(0, dot);
            realTable = table.substring(dot + 1);
        }
        if (realSchema == null) realSchema = "";
        TableMeta tm = tableMetaRepo
                .findByDataSourceIdAndDatabaseNameAndSchemaNameAndTableName(
                        dataSourceId, database, realSchema, realTable)
                .orElseThrow(() -> new BizException("表结构未同步,请先刷新元数据"));
        TableMetaVO vo = new TableMetaVO();
        vo.setDataSourceId(dataSourceId);
        vo.setDatabaseName(database);
        vo.setSchemaName(realSchema.isBlank() ? null : realSchema);
        vo.setTableName(realTable);
        vo.setTableComment(tm.getTableComment());
        vo.setColumns(fromJson(tm.getColumnsJson()));
        vo.setSyncedAt(tm.getSyncedAt());
        return vo;
    }

    public record SyncResult(int databaseCount, int tableCount, int columnCount, String schemaName) {}
}
