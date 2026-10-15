package com.nl2sql.meta.repo;

import com.nl2sql.meta.entity.DatabaseMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatabaseRepository extends JpaRepository<DatabaseMeta, Long> {
    List<DatabaseMeta> findByDataSourceIdOrderByName(Long dataSourceId);
    Optional<DatabaseMeta> findByDataSourceIdAndName(Long dataSourceId, String name);
    void deleteByDataSourceId(Long dataSourceId);
}
