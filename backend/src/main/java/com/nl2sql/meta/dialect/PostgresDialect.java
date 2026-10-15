package com.nl2sql.meta.dialect;

import com.nl2sql.meta.dto.ColumnMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL 方言实现
 *
 * - listDatabases  : 列出所有非模板库(不过滤具体库名, 用户可选 postgres 库)
 * - listTables     : 列当前连接库(由 JDBC url 决定)下的 user tables
 * - describeTable  : 描述当前连接库下某张表的列
 */
public class PostgresDialect implements DbDialect {

    @Override public String type() { return "POSTGRESQL"; }

    @Override
    public boolean matches(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listDatabases(Connection conn) throws Exception {
        // PostgreSQL 中 "database" = 物理数据库, schema = namespace
        // 此方法只列物理库(来自 pg_database), 不混入 pg_namespace
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT datname FROM pg_database " +
                        "WHERE datistemplate = false " +
                        "  AND datname NOT IN ('template0','template1') " +
                        "ORDER BY datname");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    @Override
    public List<String> listSchemas(Connection conn, String database) throws Exception {
        // schema 来自 pg_namespace, 只列用户 schema (排除系统 schema)
        // 依赖: 连接已经 USE 到某物理库, pg_namespace 反映该库下的 namespace
        // 注意: 如果用户配置的 databaseName 与物理库名不同, 需要 ALTER DATABASE 或另开连接
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nspname FROM pg_namespace " +
                        "WHERE nspname NOT IN ('pg_catalog','information_schema') " +
                        "  AND nspname NOT LIKE 'pg_toast%' " +
                        "  AND nspname NOT LIKE 'pg_temp_%' " +
                        "  AND nspname NOT LIKE 'pg_toast_temp_%' " +
                        "ORDER BY nspname");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    @Override
    public List<String> listTables(Connection conn, String database) throws Exception {
        return listTables(conn, database, null);
    }

    @Override
    public List<String> listTables(Connection conn, String database, String schema) throws Exception {
        List<String> out = new ArrayList<>();
        String sql;
        if (schema != null && !schema.isBlank()) {
            // 显式指定 schema: 只查该 schema 下的表(返回纯表名)
            sql = "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString("tablename"));
                }
            }
        } else {
            // 未指定 schema: 查所有用户 schema 的表, 返回 "schema.tablename" 格式(向后兼容老数据)
            sql = "SELECT schemaname, tablename FROM pg_tables " +
                    "WHERE schemaname NOT IN ('pg_catalog','information_schema') " +
                    "  AND schemaname NOT LIKE 'pg_toast%' " +
                    "  AND schemaname NOT LIKE 'pg_temp_%' " +
                    "ORDER BY schemaname, tablename";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("schemaname") + "." + rs.getString("tablename"));
                }
            }
        }
        return out;
    }

    @Override
    public List<ColumnMeta> describeTable(Connection conn, String database, String table) throws Exception {
        List<ColumnMeta> cols = new ArrayList<>();
        // 支持两种格式:
        // 1. table="snapshot"         → 用 database 作为 schema
        // 2. table="db4ai.snapshot"   → 自动拆分出 schema
        String schema;
        String realTable;
        if (table != null && table.contains(".")) {
            int dot = table.indexOf('.');
            schema = table.substring(0, dot);
            realTable = table.substring(dot + 1);
        } else {
            schema = (database != null && !database.isBlank()) ? database : "public";
            realTable = (table != null && !table.isBlank()) ? table : "";
        }
        String sql =
                "SELECT a.attname AS col_name, format_type(a.atttypid, a.atttypmod) AS col_type, " +
                "    t.typname AS base_type, a.atttypmod AS length_mod, " +
                "    NOT a.attnotnull AS nullable, " +
                "    EXISTS(SELECT 1 FROM pg_index i WHERE i.indrelid = c.oid AND i.indisprimary AND a.attnum = ANY(i.indkey)) AS is_pk, " +
                "        col_description(c.oid, a.attnum) AS comment, " +
                "    a.attnum AS ordinal " +
                "FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid " +
                "JOIN pg_type t ON t.oid = a.atttypid JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE c.relname = ? AND n.nspname = ? AND a.attnum > 0 AND NOT a.attisdropped " +
                "ORDER BY a.attnum";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realTable);
            ps.setString(2, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("col_type");
                    Integer length = null;
                    Integer lenMod = (Integer) rs.getObject("length_mod");
                    if (lenMod != null && lenMod > 0) length = lenMod;
                    cols.add(new ColumnMeta(
                            rs.getString("col_name"),
                            type,
                            length,
                            rs.getBoolean("nullable"),
                            rs.getBoolean("is_pk"),
                            rs.getString("comment"),
                            rs.getInt("ordinal")
                    ));
                }
            }
        }
        return cols;
    }
}
