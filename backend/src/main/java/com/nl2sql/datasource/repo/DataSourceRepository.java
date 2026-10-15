package com.nl2sql.datasource.repo;

import com.nl2sql.datasource.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    List<DataSource> findAllByEnabledTrueOrderByIdAsc();
}
