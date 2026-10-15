package com.nl2sql.skill.repo;

import com.nl2sql.skill.entity.SkillTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillTemplateRepository extends JpaRepository<SkillTemplate, Long> {
    List<SkillTemplate> findAllByEnabledTrueOrderByIdAsc();
    Optional<SkillTemplate> findFirstByIsDefaultTrueAndEnabledTrue();
}
