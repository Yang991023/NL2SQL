package com.nl2sql.model.repo;

import com.nl2sql.model.entity.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmConfigRepository extends JpaRepository<LlmConfig, Long> {
    List<LlmConfig> findAllByEnabledTrueOrderByIdAsc();
    Optional<LlmConfig> findFirstByIsDefaultTrueAndEnabledTrue();
}
