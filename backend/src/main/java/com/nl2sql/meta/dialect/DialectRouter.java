package com.nl2sql.meta.dialect;

import com.nl2sql.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

/**
 * 方言路由器 - 根据数据源类型选具体方言
 */
@Slf4j
@Component
public class DialectRouter {

    private final List<DbDialect> dialects = List.of(
            new MySqlDialect(),
            new PostgresDialect(),
            new OracleDialect()
    );

    public DbDialect route(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            throw new BizException("数据库类型为空,无法选择方言");
        }
        return switch (dbType.toUpperCase()) {
            case "MYSQL" -> dialects.get(0);
            case "POSTGRES", "POSTGRESQL" -> dialects.get(1);
            case "ORACLE" -> dialects.get(2);
            default -> throw new BizException("不支持的数据库类型: " + dbType);
        };
    }
}
