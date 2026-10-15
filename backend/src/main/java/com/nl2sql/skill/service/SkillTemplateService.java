package com.nl2sql.skill.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.skill.dto.SkillTemplateRequest;
import com.nl2sql.skill.dto.SkillTemplateVO;
import com.nl2sql.skill.entity.SkillTemplate;
import com.nl2sql.skill.repo.SkillTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillTemplateService {

    private final SkillTemplateRepository repo;

    /**
     * 内置默认 Skill Prompt 模板 (查询面板可视化配置生成 SQL)
     * 特色: 将字段的【字段名、类型、注释、业务描述、查询逻辑、值、关联表字段】整合进表结构，
     *       让大模型看一个字段就知道它是什么、做什么过滤、怎么关联，保证 SQL 准确性。
     */
    public static final String DEFAULT_PROMPT = """
            # 角色
            你是专业的 SQL 生成专家，根据以下数据库表结构和用户配置，生成准确、规范的 SQL 查询语句。

            # 数据库方言
            {{db_type}}

            # 涉及的表
            {{table_list}}

            # 表结构（含业务语义、查询条件、关联关系）
            {{full_field_meta}}

            # 已配置的关联关系（等值 JOIN 条件）
            {{join_condition}}

            # 用户原始需求
            {{user_nl_query}}

            # 输出要求
            1. 只输出 SQL，不要任何解释、注释或前缀
            2. 只允许 SELECT，严禁 INSERT/UPDATE/DELETE/DROP/TRUNCATE
            3. 使用 schema.table 形式引用表
            4. 主动追加 LIMIT 1000
            5. 如果【表结构】中有【已配置条件】，必须将其翻译为对应 SQL WHERE 条件
            6. 如果【表结构】中有【关联字段】，必须写出对应的 JOIN 子句（ON 条件为等值连接）
            7. 优先使用【业务描述】理解字段语义，避免误用字段
            8. 【LIKE / NOT LIKE 大小写规则】按 db_type 处理:
               - MySQL:    默认 LIKE 不分大小写；要分大小写用 `LIKE BINARY`  / `NOT LIKE BINARY`
               - PostgreSQL: 默认 LIKE 分大小写；要不分大小写用 `ILIKE` / `NOT ILIKE`
               - Oracle:   默认 LIKE 分大小写；要不分大小写用 `LOWER(col) LIKE LOWER(...)`
               提示词中已给出"(大小写: 区分 / 不区分)"，请按上述规则选用关键字
            """;

    /**
     * NL 描述生成 SQL 模板 (自然语言 + 表结构 + 业务描述)
     * 特色: 将表结构与字段业务描述整合，让大模型通过自然语言理解用户意图并生成 SQL
     */
    public static final String NL_ONLY_PROMPT = """
            # 角色
            你是专业的 SQL 生成专家，根据用户的自然语言描述和数据库表结构，生成准确、规范的 SQL 查询语句。

            # 数据库方言
            {{db_type}}

            # 涉及的表（仅限以下表，禁止使用其他表）
            {{table_list}}

            # 表结构（含字段类型、数据库注释、业务描述）
            {{table_meta}}

            # 字段业务描述（用户自定义的业务语义）
            {{field_biz_meta}}

            # 用户原始需求
            {{user_nl_query}}

            # 输出要求
            1. 只输出 SQL，不要任何解释、注释或前缀
            2. 只允许 SELECT，严禁 INSERT/UPDATE/DELETE/DROP/TRUNCATE
            3. 使用 schema.table 形式引用表
            4. 主动追加 LIMIT 1000
            5. 优先使用【字段业务描述】理解字段语义，生成准确的 WHERE 和 JOIN 条件
            6. 如果用户需求涉及多表，必须推断出合理的 JOIN 关系
            """;

    public List<SkillTemplateVO> list() {
        return repo.findAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public SkillTemplateVO save(SkillTemplateRequest req) {
        SkillTemplate s = req.getId() == null ? new SkillTemplate() : repo.findById(req.getId())
                .orElseThrow(() -> new BizException("模板不存在"));
        s.setName(req.getName());
        s.setContent(req.getContent());
        s.setDescription(req.getDescription());
        s.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            repo.findAllByEnabledTrueOrderByIdAsc().forEach(x -> {
                if (!x.getId().equals(s.getId())) {
                    x.setIsDefault(false);
                    repo.save(x);
                }
            });
            s.setIsDefault(true);
        } else {
            s.setIsDefault(false);
        }
        return toVO(repo.save(s));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new BizException("模板不存在");
        repo.deleteById(id);
    }

    public SkillTemplate getDefault() {
        return repo.findFirstByIsDefaultTrueAndEnabledTrue()
                .orElseThrow(() -> new BizException("无可用模板,请先在 Skill 模板管理中添加默认模板"));
    }

    /** 按名称查找已启用的模板 */
    public SkillTemplate getByName(String name) {
        return repo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()) && name.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new BizException("未找到模板 [" + name + "],请在 Skill 模板管理中添加"));
    }

    private SkillTemplateVO toVO(SkillTemplate s) {
        SkillTemplateVO v = new SkillTemplateVO();
        v.setId(s.getId());
        v.setName(s.getName());
        v.setContent(s.getContent());
        v.setDescription(s.getDescription());
        v.setIsDefault(s.getIsDefault());
        v.setEnabled(s.getEnabled());
        v.setCreatedAt(s.getCreatedAt());
        return v;
    }
}
