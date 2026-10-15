package com.nl2sql.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.ai.service.AiClient;
import com.nl2sql.canvas.dto.CanvasSaveRequest;
import com.nl2sql.canvas.service.CanvasService;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.field.service.FieldBizMetaService;
import com.nl2sql.meta.entity.TableMeta;
import com.nl2sql.meta.repo.TableMetaRepository;
import com.nl2sql.project.entity.ProjectTableField;
import com.nl2sql.project.repo.ProjectTableFieldRepository;
import com.nl2sql.project.repo.ProjectTableMetaRepository;
import com.nl2sql.skill.entity.SkillTemplate;
import com.nl2sql.skill.service.SkillTemplateService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQL 生成服务 - 拼装 Prompt + 调 LLM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGenerateService {

    private final DataSourceRepository dataSourceRepo;
    private final TableMetaRepository tableMetaRepo;
    private final FieldBizMetaService fieldBizMetaService;
    private final ProjectTableFieldRepository projectFieldRepo;
    private final ProjectTableMetaRepository projectMetaRepo;
    private final SkillTemplateService skillTemplateService;
    private final AiClient aiClient;
    private final CanvasService canvasService;
    private final ObjectMapper json = new ObjectMapper();

    @Data
    public static class GenerateRequest {
        /** 真实数据源模式（需关联数据库） */
        private Long dataSourceId;
        /** 项目表模式专用：项目表记录ID（不依赖真实数据源） */
        private Long projectTableId;
        private String databaseName;
        /** 涉及的表名列表 (如 ["public.users","public.orders"]) */
        private List<String> tables;
        /** 字段配置: [{table, column, bizDesc, filterLogic, filterValue}] */
        private List<FieldConfig> fields;
        /** 关联关系 */
        private List<JoinConfig> joins;
        /** 自然语言描述 */
        private String userNlQuery;
        /** 可选 - 使用的 Skill 模板 ID */
        private Long skillId;
    }

    @Data
    public static class FieldConfig {
        private String table;
        private String column;
        private String bizDesc;
        /** 已配置的过滤逻辑（空/IN/>=/BETWEEN/NE 等） */
        private String filterLogic;
        /** 过滤值 */
        private String filterValue;
        /** 第二段范围操作符 */
        private String filterLogic2;
        /** 第二段范围值 */
        private String filterValue2;
        /** 已配置的关联表字段（joinTable.joinField），为空则表示不关联 */
        private String joinTable;
        private String joinField;
    }

    @Data
    public static class JoinConfig {
        private String leftTable;
        private String leftColumn;
        private String rightTable;
        private String rightColumn;
    }

    @Data
    public static class GenerateResponse {
        private String sql;
        private String prompt;       // 实际发给 LLM 的完整 prompt(调试用)
        private List<String> warnings;
    }

    public GenerateResponse generate(GenerateRequest req) {
        DataSource ds = dataSourceRepo.findById(req.getDataSourceId())
                .orElseThrow(() -> new BizException("数据源不存在"));

        // 1. 拼装变量
        String tableList = buildTableList(req.getTables());
        String tableMeta = buildTableMeta(req.getDataSourceId(), req.getTables());
        String fieldBiz = buildFieldBizMeta(req.getDataSourceId(), req.getFields());
        String filterCondition = buildFilterCondition(req.getFields());
        String joinCondition = buildJoinCondition(req.getJoins());

        // 2. 选模板
        SkillTemplate tpl = req.getSkillId() != null
                ? skillTemplateService.list().stream()
                .filter(s -> s.getId().equals(req.getSkillId()))
                .findFirst().map(s -> {
                    SkillTemplate x = new SkillTemplate();
                    x.setContent(s.getContent());
                    return x;
                }).orElseGet(skillTemplateService::getDefault)
                : skillTemplateService.getDefault();

        // 3. 替换变量
        String prompt = tpl.getContent()
                .replace("{{db_type}}", safe(ds.getDbType()))
                .replace("{{table_list}}", safe(tableList))
                .replace("{{table_meta}}", safe(tableMeta))
                .replace("{{field_biz_meta}}", safe(fieldBiz))
                .replace("{{filter_condition}}", safe(filterCondition))
                .replace("{{join_condition}}", safe(joinCondition))
                .replace("{{user_nl_query}}", safe(req.getUserNlQuery()));

        // 4. 调用 AI
        String sql = aiClient.chat("你是 SQL 生成专家", prompt);

        GenerateResponse r = new GenerateResponse();
        r.setSql(sql);
        r.setPrompt(prompt);
        r.setWarnings(new ArrayList<>());
        return r;
    }

    /**
     * 【查询面板生成SQL】使用可视化配置区的内容。
     * 将字段的【字段名、类型、注释、业务描述、查询逻辑、值、关联表字段】整合进统一结构，
     * 通过 {{full_field_meta}} 变量传给 LLM，保证准确性。
     */
    public GenerateResponse generateFromCanvas(GenerateRequest req) {
        // 项目表路径（不依赖真实数据源）
        if (req.getProjectTableId() != null) {
            // 查询面板模式下 fields 里已有 bizDesc，直接用
            return generateFromProjectTable(req.getProjectTableId(), req.getUserNlQuery());
        }
        if (req.getDataSourceId() == null) {
            throw new BizException(400, "请先选择数据源");
        }
        DataSource ds = dataSourceRepo.findById(req.getDataSourceId())
                .orElseThrow(() -> new BizException("数据源不存在"));

        String tableList = buildTableList(req.getTables());
        String fullFieldMeta = buildFullFieldMeta(req.getDataSourceId(), req.getTables(), req.getFields());
        String joinCondition = buildJoinCondition(req.getJoins());

        // 使用 "查询面板生成SQL" 模板
        SkillTemplate tpl = skillTemplateService.getByName("查询面板生成SQL");

        String prompt = tpl.getContent()
                .replace("{{db_type}}", safe(ds.getDbType()))
                .replace("{{table_list}}", safe(tableList))
                .replace("{{full_field_meta}}", safe(fullFieldMeta))
                .replace("{{join_condition}}", safe(joinCondition))
                .replace("{{user_nl_query}}", safe(req.getUserNlQuery()));

        String sql = aiClient.chat("你是 SQL 生成专家", prompt);

        GenerateResponse r = new GenerateResponse();
        r.setSql(sql);
        r.setPrompt(prompt);
        r.setWarnings(new ArrayList<>());
        return r;
    }

    /**
     * 构建完整字段信息：将【字段名、类型、注释、业务描述、查询逻辑、值、关联表字段】
     * 整合进统一结构，每行代表一个字段的配置上下文。
     *
     * 格式示例:
     * ## schema.table
     *   - column_name (type) -- 数据库注释 [PK] [已配置条件: field = 'xxx'] [关联到: other_table.other_column]
     *     业务描述: xxx
     *
     * @param dataSourceId 用于查询表元数据（必须传真实 dataSourceId，不能用 0L）
     */
    private String buildFullFieldMeta(Long dataSourceId, List<String> tables, List<FieldConfig> fields) {
        if (tables == null || tables.isEmpty()) return "(无表)";

        // 建立字段配置索引: table.column -> bizDesc
        Map<String, String> bizDescMap = new HashMap<>();
        Map<String, FieldConfig> fieldConfigMap = new HashMap<>();
        if (fields != null) {
            for (FieldConfig f : fields) {
                if (f.getTable() != null && f.getColumn() != null) {
                    String key = f.getTable() + "." + f.getColumn();
                    if (f.getBizDesc() != null && !f.getBizDesc().isBlank()) {
                        bizDescMap.put(key, f.getBizDesc());
                    }
                    fieldConfigMap.put(key, f);
                }
            }
        }

        // 按表分组构建
        StringBuilder sb = new StringBuilder();
        for (String t : tables) {
            // 解析 schema.table
            String schema = "";
            String tableName = t;
            if (t.contains(".")) {
                int idx = t.indexOf('.');
                schema = t.substring(0, idx);
                tableName = t.substring(idx + 1);
            }
            String fullName = t;
            final String finalFullName = fullName;

            // 查找表元数据
            // 1) 精确按 (dataSourceId, databaseName=schema, tableName) 查
            // 2) 找不到时, 用 tableName 在该数据源下做模糊匹配(兼容老数据或同步脏数据)
            // 注意: t_table_meta.database_name 存的是 schema 前缀(如 db4ai), 不是用户选的数据库(如 postgres)
            // tables 里的格式是 schema.table, 所以直接用 schema 做 database_name 查询
            final String finalSchema = schema;
            final String finalTableName = tableName;
            List<TableMeta> tms;
            if (finalSchema.isEmpty()) {
                tms = tableMetaRepo.findAll().stream()
                        .filter(x -> x.getTableName().equalsIgnoreCase(finalTableName)).toList();
            } else {
                tms = tableMetaRepo.findByDataSourceIdAndDatabaseNameAndTableName(dataSourceId, finalSchema, finalTableName)
                        .stream().toList();
                if (tms.isEmpty()) {
                    // 兜底: 按 tableName 在该数据源下模糊查(忽略 schema/database)
                    tms = tableMetaRepo.findAll().stream()
                            .filter(x -> x.getDataSourceId() != null
                                    && x.getDataSourceId().equals(dataSourceId)
                                    && x.getTableName().equalsIgnoreCase(finalTableName))
                            .toList();
                }
            }
            if (tms.isEmpty()) {
                sb.append("## ").append(fullName).append(" (未找到元数据)\n");
                continue;
            }

            TableMeta tm = tms.get(0);
            sb.append("## ").append(finalFullName).append("\n");
            try {
                List<com.nl2sql.meta.dto.ColumnMeta> cols = json.readValue(tm.getColumnsJson(),
                        json.getTypeFactory().constructCollectionType(List.class,
                                com.nl2sql.meta.dto.ColumnMeta.class));
                for (com.nl2sql.meta.dto.ColumnMeta c : cols) {
                    String key = finalFullName + "." + c.getName();
                    FieldConfig fc = fieldConfigMap.get(key);
                    StringBuilder line = new StringBuilder();
                    line.append("  - ").append(c.getName()).append(" (").append(c.getType()).append(")");
                    if (c.getComment() != null && !c.getComment().isBlank()) {
                        line.append(" -- ").append(c.getComment());
                    }
                    if (Boolean.TRUE.equals(c.getIsPk())) {
                        line.append(" [PK]");
                    }

                    // 查询逻辑 + 值
                    if (fc != null && fc.getFilterLogic() != null && !fc.getFilterLogic().isBlank()
                            && !"NULL".equalsIgnoreCase(fc.getFilterLogic())) {
                        line.append(" [已配置条件: ");
                        String logic = fc.getFilterLogic();
                        String val1 = fc.getFilterValue() == null ? "" : fc.getFilterValue();
                        if ("RANGE_AND".equalsIgnoreCase(logic) || "RANGE_OR".equalsIgnoreCase(logic)) {
                            String op1 = logic;
                            String op2 = fc.getFilterLogic2() == null
                                    ? ">=".equals(op1) ? "<=" : "<="
                                    : fc.getFilterLogic2();
                            String val2 = fc.getFilterValue2() == null ? "" : fc.getFilterValue2();
                            if ("RANGE_AND".equalsIgnoreCase(logic)) {
                                line.append(op1).append(" ").append(val1).append(" 且 ").append(op2).append(" ").append(val2);
                            } else {
                                line.append(op1).append(" ").append(val1).append(" 或 ").append(op2).append(" ").append(val2);
                            }
                        } else if ("NE".equalsIgnoreCase(logic)) {
                            line.append("≠ ").append(val1);
                        } else if ("LIKE".equalsIgnoreCase(logic) || "NOT LIKE".equalsIgnoreCase(logic)) {
                            // 包含/不包含: filterLogic2 = 大小写标记 (CASE_SENSITIVE / CASE_INSENSITIVE / 旧值=位置)
                            // 渲染为带大小写说明的 SQL 片段, 由 LLM 根据 db_type 选用 LIKE / LIKE BINARY / ILIKE
                            String caseInfo = mapCaseInfo(fc.getFilterLogic2());
                            String literal = renderLikeLiteral(val1);
                            line.append(logic.toUpperCase()).append(" ").append(literal)
                                .append(" (大小写: ").append(caseInfo).append(")");
                        } else {
                            line.append(logic).append(" ").append(val1);
                        }
                        line.append("]");
                    }

                    // 关联表字段: 标注此字段 JOIN 到了右表的哪个字段
                    if (fc != null && fc.getJoinTable() != null && fc.getJoinField() != null) {
                        line.append(" [关联到: ").append(fc.getJoinTable()).append(".").append(fc.getJoinField()).append("]");
                    }

                    line.append("\n");

                    // 业务描述
                    String bizDesc = bizDescMap.get(key);
                    if (bizDesc != null && !bizDesc.isBlank()) {
                        line.append("    业务描述: ").append(bizDesc).append("\n");
                    }

                    sb.append(line);
                }
            } catch (Exception ignore) {
            }
        }
        return sb.toString();
    }

    /**
     * 【自然语言描述生成SQL】仅使用 NL 描述 + 表范围。
     * fields 和 joins 仅包含表的基本信息（不含用户配置的过滤逻辑）。
     * 自动使用 "自然语言描述生成SQL" skill 模板（由 DevDataSeeder 初始化）。
     */
    public GenerateResponse generateFromNl(GenerateRequest req) {
        // 优先走项目表路径（不依赖真实数据源）
        if (req.getProjectTableId() != null) {
            return generateFromProjectTable(req.getProjectTableId(), req.getUserNlQuery());
        }
        if (req.getDataSourceId() == null) {
            throw new BizException(400, "请先选择数据源");
        }
        DataSource ds = dataSourceRepo.findById(req.getDataSourceId())
                .orElseThrow(() -> new BizException("数据源不存在"));

        String tableList = buildTableList(req.getTables());
        String tableMeta = buildTableMeta(req.getDataSourceId(), req.getTables());
        String fieldBiz = buildFieldBizMeta(req.getDataSourceId(), req.getFields());

        // 使用 "自然语言描述生成SQL" 模板
        SkillTemplate tpl = skillTemplateService.getByName("自然语言描述生成SQL");

        String prompt = tpl.getContent()
                .replace("{{db_type}}", safe(ds.getDbType()))
                .replace("{{table_list}}", safe(tableList))
                .replace("{{table_meta}}", safe(tableMeta))
                .replace("{{field_biz_meta}}", safe(fieldBiz))
                .replace("{{user_nl_query}}", safe(req.getUserNlQuery()));

        String sql = aiClient.chat("你是 SQL 生成专家", prompt);

        GenerateResponse r = new GenerateResponse();
        r.setSql(sql);
        r.setPrompt(prompt);
        r.setWarnings(new ArrayList<>());
        return r;
    }

    /**
     * 【项目表模式】直接读取项目表管理的字段结构（不依赖真实数据源）。
     * @param projectTableId 项目表记录 ID
     * @param userNlQuery    用户自然语言描述（可空）
     */
    public GenerateResponse generateFromProjectTable(Long projectTableId, String userNlQuery) {
        if (projectTableId == null) {
            throw new BizException(400, "projectTableId 不能为空");
        }
        var meta = projectMetaRepo.findById(projectTableId)
                .orElseThrow(() -> new BizException(404, "项目表不存在: " + projectTableId));

        List<ProjectTableField> fields = projectFieldRepo.findByProjectTableIdOrderByIdAsc(projectTableId);
        if (fields.isEmpty()) {
            throw new BizException(400, "项目表【" + meta.getTableName() + "】未配置字段");
        }

        // 拼接字段元数据（与 generateFromCanvas 的格式保持一致）
        StringBuilder fieldMeta = new StringBuilder();
        fieldMeta.append("## ").append(meta.getTableName()).append("\n");
        for (ProjectTableField f : fields) {
            fieldMeta.append("  - ").append(f.getColumnName())
                     .append(" ").append(f.getColumnType() == null ? "" : f.getColumnType());
            if (f.getColumnComment() != null && !f.getColumnComment().isBlank()) {
                fieldMeta.append(" -- ").append(f.getColumnComment());
            }
            fieldMeta.append("\n");
            if (f.getBizDesc() != null && !f.getBizDesc().isBlank()) {
                fieldMeta.append("    业务描述: ").append(f.getBizDesc()).append("\n");
            }
        }

        // 使用"自然语言描述生成SQL"模板（项目表用通用模板即可，不强求方言）
        SkillTemplate tpl = skillTemplateService.getByName("自然语言描述生成SQL");

        String prompt = tpl.getContent()
                .replace("{{db_type}}", "通用 SQL（不指定方言）")
                .replace("{{table_list}}", "- " + meta.getTableName() + " (项目编号: " + meta.getProjectCode()
                        + (meta.getProjectName() == null || meta.getProjectName().isBlank()
                                ? "" : ", 项目名称: " + meta.getProjectName()) + ")")
                .replace("{{table_meta}}", fieldMeta.toString())
                .replace("{{field_biz_meta}}", "")
                .replace("{{user_nl_query}}", safe(userNlQuery));

        String sql = aiClient.chat("你是 SQL 生成专家", prompt);

        GenerateResponse r = new GenerateResponse();
        r.setSql(sql);
        r.setPrompt(prompt);
        r.setWarnings(new ArrayList<>());
        return r;
    }

    public GenerateResponse saveCanvas(CanvasSaveRequest req) {
        var r = canvasService.save(req);
        var resp = new GenerateResponse();
        resp.setSql(""); resp.setPrompt(""); resp.setWarnings(List.of());
        return resp;
    }

    private String buildTableList(List<String> tables) {
        if (tables == null || tables.isEmpty()) return "(无)";
        return tables.stream()
                .map(t -> "- " + t)
                .collect(Collectors.joining("\n"));
    }

    private String buildTableMeta(Long dataSourceId, List<String> tables) {
        if (tables == null || tables.isEmpty()) return "(无)";
        StringBuilder sb = new StringBuilder();
        for (String t : tables) {
            // 解析 schema.table (database_name 存的是 schema 前缀, 如 db4ai)
            String schema = "";
            String tableName = t;
            if (t.contains(".")) {
                int idx = t.indexOf('.');
                schema = t.substring(0, idx);
                tableName = t.substring(idx + 1);
            }
            final String finalSchema = schema;
            final String finalTableName = tableName;
            List<TableMeta> tms;
            if (finalSchema.isEmpty()) {
                tms = tableMetaRepo.findAll().stream()
                        .filter(x -> x.getTableName().equalsIgnoreCase(finalTableName)).toList();
            } else {
                tms = tableMetaRepo.findByDataSourceIdAndDatabaseNameAndTableName(dataSourceId, finalSchema, finalTableName)
                        .stream().toList();
                if (tms.isEmpty()) {
                    // 兜底: 按 tableName 在该数据源下模糊查
                    tms = tableMetaRepo.findAll().stream()
                            .filter(x -> x.getDataSourceId() != null
                                    && x.getDataSourceId().equals(dataSourceId)
                                    && x.getTableName().equalsIgnoreCase(finalTableName))
                            .toList();
                }
            }
            if (tms.isEmpty()) {
                sb.append("## ").append(t).append(" (未在元数据中)\n");
                continue;
            }
            TableMeta tm = tms.get(0);
            sb.append("## ").append(t).append("\n");
            try {
                List<com.nl2sql.meta.dto.ColumnMeta> cols = json.readValue(tm.getColumnsJson(),
                        json.getTypeFactory().constructCollectionType(List.class,
                                com.nl2sql.meta.dto.ColumnMeta.class));
                for (com.nl2sql.meta.dto.ColumnMeta c : cols) {
                    sb.append("  - ").append(c.getName())
                      .append(" ").append(c.getType());
                    if (Boolean.TRUE.equals(c.getIsPk())) sb.append(" [PK]");
                    if (c.getComment() != null && !c.getComment().isBlank()) {
                        sb.append(" -- ").append(c.getComment());
                    }
                    sb.append("\n");
                }
            } catch (Exception ignore) {
            }
        }
        return sb.toString();
    }

    private String buildFieldBizMeta(Long dataSourceId, List<FieldConfig> fields) {
        if (fields == null || fields.isEmpty()) return "(无)";
        return fields.stream()
                .filter(f -> f.getBizDesc() != null && !f.getBizDesc().isBlank())
                .map(f -> "- " + f.getTable() + "." + f.getColumn() + ": " + f.getBizDesc())
                .collect(Collectors.joining("\n"));
    }

    private String buildFilterCondition(List<FieldConfig> fields) {
        if (fields == null) return "(无)";
        return fields.stream()
                .filter(f -> f.getFilterLogic() != null && !f.getFilterLogic().isBlank()
                        && !"NULL".equalsIgnoreCase(f.getFilterLogic()))
                .map(f -> {
                    String logic = f.getFilterLogic();
                    String val1 = f.getFilterValue() == null ? "" : f.getFilterValue();
                    // 双段范围
                    if ("RANGE_AND".equalsIgnoreCase(logic) || "RANGE_OR".equalsIgnoreCase(logic)) {
                        String op1 = f.getFilterLogic();
                        String op2 = f.getFilterLogic2() == null ? ">".equals(op1) ? "<" : "<" : f.getFilterLogic2();
                        String val2 = f.getFilterValue2() == null ? "" : f.getFilterValue2();
                        if ("RANGE_AND".equalsIgnoreCase(logic)) {
                            return "- " + f.getTable() + "." + f.getColumn()
                                    + " " + op1 + " " + val1 + " 且 " + op2 + " " + val2;
                        } else {
                            return "- " + f.getTable() + "." + f.getColumn()
                                    + " " + op1 + " " + val1 + " 或 " + op2 + " " + val2;
                        }
                    }
                    // 不等于
                    if ("NE".equalsIgnoreCase(logic)) {
                        return "- " + f.getTable() + "." + f.getColumn() + " ≠ " + val1;
                    }
                    // 包含/不包含: filterLogic2 = 大小写标记 (CASE_SENSITIVE / CASE_INSENSITIVE / 旧值=位置)
                    if ("LIKE".equalsIgnoreCase(logic) || "NOT LIKE".equalsIgnoreCase(logic)) {
                        String caseInfo = mapCaseInfo(f.getFilterLogic2());
                        String literal = renderLikeLiteral(val1);
                        return "- " + f.getTable() + "." + f.getColumn() + " " + logic.toUpperCase() + " " + literal
                                + " (大小写: " + caseInfo + ")";
                    }
                    return "- " + f.getTable() + "." + f.getColumn()
                            + " " + logic + " " + val1;
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildJoinCondition(List<JoinConfig> joins) {
        if (joins == null || joins.isEmpty()) return "(无)";
        return joins.stream()
                .map(j -> "- " + j.getLeftTable() + "." + j.getLeftColumn()
                        + " = " + j.getRightTable() + "." + j.getRightColumn())
                .collect(Collectors.joining("\n"));
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * 把 (原值) 渲染为可直接放进 WHERE 子句的 SQL LIKE 字面量
     * - 始终返回 '%value%'  (子串匹配, 任何位置都视为"包含")
     * - 单引号已转义
     * - value 中的 % 和 _ 默认按字面量处理(反转义), 避免被 SQL 解释为通配符
     * - 大小写区分由调用方配合 db_type 使用 LIKE / LIKE BINARY / ILIKE
     */
    private String renderLikeLiteral(String rawValue) {
        String v = rawValue == null ? "" : rawValue;
        // 转义 SQL 单引号 + 反转义 LIKE 通配符(让用户输入的 % 和 _ 当字面量)
        String escaped = v.replace("'", "''")
                          .replace("\\", "\\\\")
                          .replace("%", "\\%")
                          .replace("_", "\\_");
        return "'%" + escaped + "%'";
    }

    /**
     * 把 filterLogic2 映射成人类可读的"区分大小写 / 不区分大小写"
     * 兼容旧值 (ANY/START/END/EXACT), 一律视为"不区分" (因为以前没有大小写选项, 实际 SQL 由 db 决定)
     */
    private String mapCaseInfo(String flag) {
        if (flag == null || flag.isBlank()) return "不区分";
        String f = flag.toUpperCase();
        if ("CASE_SENSITIVE".equals(f)) return "区分";
        if ("CASE_INSENSITIVE".equals(f)) return "不区分";
        // 旧值 (ANY/START/END/EXACT) 视为"不区分"
        return "不区分";
    }
}
