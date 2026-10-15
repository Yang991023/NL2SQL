package com.nl2sql.datasource.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.datasource.dto.ConnTestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * 通用连接测试工具 - 用 JDBC 直连测试可达性与账号密码，
 * 并自动识别数据库类型和版本。
 */
@Slf4j
@Component
public class ConnTester {

    public ConnTestResult test(String dbType, String host, int port,
                               String databaseName, String username, String password) {
        String url = buildUrl(safe(dbType, "MYSQL"), safe(host), port, safe(databaseName));
        Properties props = new Properties();
        props.setProperty("user", username);
        if (password != null) props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "10000");

        try (Connection conn = DriverManager.getConnection(url, props); Statement st = conn.createStatement()) {
            String detectedType = detectDbType(conn, dbType);
            String version = fetchVersion(conn, detectedType);
            return new ConnTestResult(true, "连接成功", detectedType, version);
        } catch (Exception e) {
            log.warn("数据库连通性测试失败: {}", e.getMessage());
            return new ConnTestResult(false, friendly(e), safe(dbType), null);
        }
    }

    private String buildUrl(String type, String host, int port, String dbName) {
        return switch (type.toUpperCase()) {
            case "MYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + (dbName == null ? "" : dbName);
            case "POSTGRES", "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port + "/" + (dbName == null ? "postgres" : dbName);
            case "ORACLE" -> "jdbc:oracle:thin:@" + host + ":" + port + ":" + (dbName == null ? "ORCL" : dbName);
            default -> throw new BizException("不支持的数据库类型: " + type);
        };
    }

    private String detectDbType(Connection conn, String declared) {
        if (declared != null && !declared.isBlank()) return declared.toUpperCase();
        try (Statement st = conn.createStatement()) {
            // 未知声明类型时,根据 productName 推断
            return conn.getMetaData().getDatabaseProductName().toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String fetchVersion(Connection conn, String type) {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            if (rs.next()) return rs.getString(1);
        } catch (Exception ignore) {
        }
        try {
            return conn.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String safe(String s, String def) { return s == null || s.isBlank() ? def : s; }

    private String friendly(Exception e) {
        String m = e.getMessage();
        if (m == null) return "连接失败";
        if (m.contains("Connection refused")) return "无法连接，地址或端口不通";
        if (m.contains("password") || m.toLowerCase().contains("authentication")) return "账号或密码错误";
        if (m.toLowerCase().contains("permission")) return "账号权限不足";
        if (m.contains("Unknown database")) return "数据库不存在";
        return "连接失败: " + m;
    }
}
