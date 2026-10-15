package com.nl2sql.meta.dialect;

import com.nl2sql.meta.dto.ColumnMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 方言实现
 */
public class MySqlDialect implements DbDialect {

    @Override public String type() { return "MYSQL"; }

    @Override
    public boolean matches(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> listDatabases(Connection conn) throws Exception {
        List<String> out = new ArrayList<>();
        // 跳过系统库
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA " +
                        "WHERE SCHEMA_NAME NOT IN ('information_schema','mysql','performance_schema','sys')");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    @Override
    public List<String> listTables(Connection conn, String database) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE='BASE TABLE'")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Override
    public List<ColumnMeta> describeTable(Connection conn, String database, String table) throws Exception {
        List<ColumnMeta> cols = new ArrayList<>();
        // 支持 "schema.table" 格式(同 PG 方言约定), MySQL 下 schema 通常 ≡ database
        String realSchema = database;
        String realTable = table;
        if (table != null && table.contains(".")) {
            int dot = table.indexOf('.');
            realSchema = table.substring(0, dot);
            realTable = table.substring(dot + 1);
        }
        String sql =
                "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, " +
                        "IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT, ORDINAL_POSITION " +
                        "FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realSchema);
            ps.setString(2, realTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnType = rs.getString("DATA_TYPE");
                    Integer len = (Integer) rs.getObject("CHARACTER_MAXIMUM_LENGTH");
                    cols.add(new ColumnMeta(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("DATA_TYPE"),
                            len,
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            "PRI".equals(rs.getString("COLUMN_KEY")),
                            rs.getString("COLUMN_COMMENT"),
                            rs.getInt("ORDINAL_POSITION")
                    ));
                }
            }
        }
        return cols;
    }
}
