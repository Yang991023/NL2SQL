package com.nl2sql.meta.dialect;

import com.nl2sql.meta.dto.ColumnMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle 方言实现 - 仅读账号场景
 * 12c+ 推荐 CDB/PDB 切换,这里用 SYS 用户体系需要业务库用户(system/dataview)
 */
public class OracleDialect implements DbDialect {

    @Override public String type() { return "ORACLE"; }

    @Override
    public boolean matches(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("oracle");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listDatabases(Connection conn) throws Exception {
        // Oracle 没有数据库概念,只有 schema/用户名,此处取当前可访问的用户名
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM all_users ORDER BY username");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString(1);
                // 过滤系统用户
                if (!u.startsWith("SYS") && !u.startsWith("SYSTEM") && !u.startsWith("OUTLN")
                        && !u.startsWith("DBSNMP") && !u.startsWith("XDB") && !u.startsWith("WMSYS")) {
                    out.add(u);
                }
            }
        }
        return out;
    }

    @Override
    public List<String> listSchemas(Connection conn, String database) throws Exception {
        // Oracle 中 "database" 参数就是 owner 名(listDatabases 已经过滤了系统用户)
        // 列出该 owner 下的所有 schema (即该 owner 本身 + 私有/公共 synonym 关联的 schema)
        // 为简单起见, 直接返回该 owner 作为唯一 schema
        List<String> out = new ArrayList<>();
        if (database != null && !database.isBlank()) {
            out.add(database.toUpperCase());
        }
        return out;
    }

    @Override
    public List<String> listTables(Connection conn, String database) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_name FROM all_tables WHERE owner = ? ORDER BY table_name")) {
            ps.setString(1, database.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Override
    public List<ColumnMeta> describeTable(Connection conn, String database, String table) throws Exception {
        List<ColumnMeta> cols = new ArrayList<>();
        // 支持 "schema.table" 格式(同 PG 方言约定), Oracle 下 schema = owner
        String realSchema = database;
        String realTable = table;
        if (table != null && table.contains(".")) {
            int dot = table.indexOf('.');
            realSchema = table.substring(0, dot);
            realTable = table.substring(dot + 1);
        }
        String sql =
                "SELECT c.column_name, c.data_type, c.data_length, c.nullable, " +
                        "    (SELECT COUNT(*) FROM all_cons_columns cc JOIN all_constraints k " +
                        "        ON cc.owner = k.owner AND cc.constraint_name = k.constraint_name " +
                        "        WHERE k.constraint_type = 'P' AND k.table_name = c.table_name " +
                        "        AND k.owner = c.owner AND cc.column_name = c.column_name) AS is_pk, " +
                        "    c.column_id AS ordinal, " +
                        "    (SELECT comments FROM all_col_comments WHERE owner = c.owner " +
                        "        AND table_name = c.table_name AND column_name = c.column_name) AS comment " +
                        "FROM all_tab_columns c " +
                        "WHERE c.owner = ? AND c.table_name = ? " +
                        "ORDER BY c.column_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realSchema.toUpperCase());
            ps.setString(2, realTable.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(new ColumnMeta(
                            rs.getString("column_name"),
                            rs.getString("data_type"),
                            (Integer) rs.getObject("data_length"),
                            "Y".equals(rs.getString("nullable")),
                            rs.getInt("is_pk") > 0,
                            rs.getString("comment"),
                            rs.getInt("ordinal")
                    ));
                }
            }
        }
        return cols;
    }
}
