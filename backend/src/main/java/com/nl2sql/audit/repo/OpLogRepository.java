package com.nl2sql.audit.repo;

import com.nl2sql.audit.entity.OpLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpLogRepository extends JpaRepository<OpLog, Long> {
    Page<OpLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<OpLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<OpLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
