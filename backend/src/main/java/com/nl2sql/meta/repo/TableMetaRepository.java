package com.nl2sql.meta.repo;

import com.nl2sql.meta.entity.TableMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TableMetaRepository extends JpaRepository<TableMeta, Long> {
    /** 仅按 (ds, db) 查表 - 旧接口, 兼容 MySQL (schema 为空) */
    List<TableMeta> findByDataSourceIdAndDatabaseName(Long dataSourceId, String databaseName);

    /** 按 (ds, db, schema, table) 唯一定位 */
    Optional<TableMeta> findByDataSourceIdAndDatabaseNameAndSchemaNameAndTableName(
            Long dataSourceId, String databaseName, String schemaName, String tableName);

    /** 旧接口 - MySQL 场景下兼容, schema_name 为空时使用 */
    default Optional<TableMeta> findByDataSourceIdAndDatabaseNameAndTableName(
            Long dataSourceId, String databaseName, String tableName) {
        return findByDataSourceIdAndDatabaseNameAndSchemaNameAndTableName(
                dataSourceId, databaseName, "", tableName);
    }

    /** 按 (ds, db, schema) 查表 - PG/Oracle 场景 */
    @Query("SELECT t FROM TableMeta t WHERE t.dataSourceId = :dsId " +
            "AND t.databaseName = :db AND (t.schemaName = :schema OR t.schemaName IS NULL) " +
            "ORDER BY t.tableName")
    List<TableMeta> findByDataSourceIdDatabaseAndSchema(
            @Param("dsId") Long dsId,
            @Param("db") String databaseName,
            @Param("schema") String schemaName);

    void deleteByDataSourceId(Long dataSourceId);
    void deleteByDataSourceIdAndDatabaseName(Long dataSourceId, String databaseName);
}
