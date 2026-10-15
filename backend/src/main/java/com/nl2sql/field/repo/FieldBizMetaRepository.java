package com.nl2sql.field.repo;

import com.nl2sql.field.entity.FieldBizMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldBizMetaRepository extends JpaRepository<FieldBizMeta, Long> {
    List<FieldBizMeta> findByDataSourceIdAndDatabaseNameAndTableName(
            Long dataSourceId, String databaseName, String tableName);
    Optional<FieldBizMeta> findByDataSourceIdAndDatabaseNameAndTableNameAndColumnName(
            Long dataSourceId, String databaseName, String tableName, String columnName);
    List<FieldBizMeta> findByDataSourceIdAndDatabaseName(
            Long dataSourceId, String databaseName);
}
