package com.nl2sql.canvas.repo;

import com.nl2sql.canvas.entity.QueryCanvas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryCanvasRepository extends JpaRepository<QueryCanvas, Long> {
    List<QueryCanvas> findAllByDataSourceIdOrderByUpdatedAtDesc(Long dataSourceId);
}
