package com.nl2sql.project.repo;

import com.nl2sql.project.entity.ProjectTableField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTableFieldRepository extends JpaRepository<ProjectTableField, Long> {

    List<ProjectTableField> findByProjectTableIdOrderByIdAsc(Long projectTableId);

    void deleteByProjectTableId(Long projectTableId);
}
