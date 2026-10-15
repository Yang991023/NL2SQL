package com.nl2sql.meta.dialect;

import com.nl2sql.meta.dto.ColumnMeta;

import java.sql.Connection;
import java.util.List;

/**
 * 数据库方言适配器 - 跨方言拉取元数据
 * 实现类: MySqlDialect / PostgresDialect / OracleDialect
 */
public interface DbDialect {

    /**
     * 数据库类型,如 MYSQL / POSTGRES / ORACLE
     */
    String type();

    /**
     * 是否支持当前连接,jdbc productName 或 version 检测
     */
    boolean matches(Connection conn);

    /**
     * 列出所有数据库/schema 名
     */
    List<String> listDatabases(Connection conn) throws Exception;

    /**
     * 列出指定数据库下的 schema 列表(可选)
     * <p>
     * - MySQL: schema ≡ database, 默认返回空列表(前端隐藏 Schema 级)
     * - PostgreSQL: 返回当前物理库下的所有用户 schema (public/db4ai/...)
     * - Oracle: 返回 database (即 owner 用户名) 下的所有 schema
     */
    default List<String> listSchemas(Connection conn, String database) throws Exception {
        return java.util.Collections.emptyList();
    }

    /**
     * 列出指定数据库下的所有表名
     * <p>
     * 子类可重写以支持 schema 隔离
     */
    List<String> listTables(Connection conn, String database) throws Exception;

    /**
     * 列出指定数据库 + schema 下的表(可选, 仅当数据库支持 schema 时使用)
     */
    default List<String> listTables(Connection conn, String database, String schema) throws Exception {
        return listTables(conn, database);
    }

    /**
     * 拉取某表的完整结构(字段列表)
     */
    List<ColumnMeta> describeTable(Connection conn, String database, String table) throws Exception;
}
