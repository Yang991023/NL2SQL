package com.nl2sql.project.repo;

import com.nl2sql.project.entity.ProjectTableMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectTableMetaRepository extends JpaRepository<ProjectTableMeta, Long> {

    /** 按项目编号精确查 */
    Optional<ProjectTableMeta> findByProjectCode(String projectCode);

    /** 同一项目下是否已存在该表名 */
    boolean existsByProjectCodeAndTableName(String projectCode, String tableName);

    /** 项目编号是否已存在 */
    boolean existsByProjectCode(String projectCode);

    /** 项目名称是否已存在 */
    boolean existsByProjectName(String projectName);

    /** 项目编号重复检查（排除自身） */
    @Query("select count(p) > 0 from ProjectTableMeta p where p.projectCode = :code and (:excludeId is null or p.id != :excludeId)")
    boolean existsProjectCodeExclude(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** 模糊搜索：项目名称或表名称 */
    @Query("select p from ProjectTableMeta p where " +
            "p.projectName like concat('%', coalesce(:projectName,''), '%') and " +
            "p.tableName like concat('%', coalesce(:tableName,''), '%') " +
            "order by p.createdAt desc")
    List<ProjectTableMeta> search(@Param("projectName") String projectName,
                                  @Param("tableName") String tableName);

    /** 同一项目下的所有表 */
    List<ProjectTableMeta> findByProjectCodeOrderByCreatedAtDesc(String projectCode);
}
