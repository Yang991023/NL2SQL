package com.nl2sql.project.service;

import com.nl2sql.ai.service.AiClient;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.model.entity.LlmConfig;
import com.nl2sql.model.service.LlmConfigService;
import com.nl2sql.project.dto.AutoFixRequest;
import com.nl2sql.project.dto.ImportScriptRequest;
import com.nl2sql.project.dto.ProjectTableMetaRequest;
import com.nl2sql.project.dto.ProjectTableMetaVO;
import com.nl2sql.project.entity.ProjectTableField;
import com.nl2sql.project.entity.ProjectTableMeta;
import com.nl2sql.project.repo.ProjectTableFieldRepository;
import com.nl2sql.project.repo.ProjectTableMetaRepository;
import com.nl2sql.security.SecurityContextUtil;
import com.nl2sql.skill.entity.SkillTemplate;
import com.nl2sql.skill.service.SkillTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectTableMetaService {

    private final ProjectTableMetaRepository metaRepo;
    private final ProjectTableFieldRepository fieldRepo;
    private final SkillTemplateService skillService;
    private final AiClient aiClient;
    private final LlmConfigService llmConfigService;

    /** 列表搜索（支持模糊） */
    public List<ProjectTableMetaVO> search(String projectName, String tableName) {
        return metaRepo.search(projectName, tableName).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /** 详情（含字段列表） */
    public ProjectTableMetaVO detail(Long id) {
        ProjectTableMeta meta = metaRepo.findById(id)
                .orElseThrow(() -> new BizException(404, "记录不存在"));
        return toVO(meta);
    }

    /** 新增或编辑 */
    @Transactional
    public ProjectTableMetaVO save(ProjectTableMetaRequest req) {
        Long currentUserId = SecurityContextUtil.currentUserId();
        ProjectTableMeta meta;

        if (req.getId() != null) {
            // ── 编辑模式：校验 + 回显基本信息不可改 ──
            meta = metaRepo.findById(req.getId())
                    .orElseThrow(() -> new BizException(404, "记录不存在"));
            // projectCode / projectName / tableName 理论上不可改，这里直接沿用
        } else {
            // ── 新增模式：同项目下不允许出现相同表名 ──
            if (metaRepo.existsByProjectCodeAndTableName(req.getProjectCode(), req.getTableName())) {
                throw new BizException(400, "该项目下已存在相同表名");
            }

            meta = new ProjectTableMeta();
            meta.setCreatedBy(currentUserId);
        }

        // 基础字段（编辑时可能不传，保持原值）
        if (req.getProjectCode() != null) meta.setProjectCode(req.getProjectCode());
        if (req.getProjectName() != null) meta.setProjectName(req.getProjectName());
        if (req.getTableName() != null) meta.setTableName(req.getTableName());

        meta = metaRepo.save(meta);

        // 保存字段（编辑模式下全量替换）
        if (req.getFields() != null) {
            if (req.getId() != null) {
                fieldRepo.deleteByProjectTableId(req.getId());
            }
            for (ProjectTableMetaRequest.FieldItemRequest fi : req.getFields()) {
                if (fi.getColumnName() == null || fi.getColumnName().isBlank()) continue;
                ProjectTableField field = new ProjectTableField();
                field.setProjectTableId(meta.getId());
                field.setColumnName(fi.getColumnName().trim());
                field.setColumnType(fi.getColumnType());
                field.setColumnComment(fi.getColumnComment());
                field.setBizDesc(fi.getBizDesc());
                fieldRepo.save(field);
            }
        }

        return detail(meta.getId());
    }

    /** 删除（连同字段） */
    @Transactional
    public void delete(Long id) {
        if (!metaRepo.existsById(id)) throw new BizException(404, "记录不存在");
        fieldRepo.deleteByProjectTableId(id);
        metaRepo.deleteById(id);
    }

    /** 导入脚本解析（生成多张表配置） */
    @Transactional
    public List<ProjectTableMetaVO> importScript(ImportScriptRequest req) {
        if (req == null || req.getScript() == null || req.getScript().isBlank()) {
            throw new BizException(400, "请填写导入脚本");
        }
        if (req.getProjectCode() == null || req.getProjectCode().isBlank()) {
            throw new BizException(400, "请填写项目编号");
        }
        if (req.getProjectName() == null || req.getProjectName().isBlank()) {
            throw new BizException(400, "请填写项目名称");
        }

        String mode = req.getMode() == null ? "ddl" : req.getMode().toLowerCase();
        List<ProjectTableMetaVO> results = new ArrayList<>();
        // 根据 mode 选择解析器
        List<ParsedTable> parsed = "list".equals(mode)
                ? parseListScript(req.getScript())
                : parseScript(req.getScript());
        Long userId = SecurityContextUtil.currentUserId();

        for (ParsedTable t : parsed) {
            // 顶部输入的项目编号/名称作为兜底（脚本内未声明时使用）
            if (t.projectCode == null || t.projectCode.isEmpty()) t.projectCode = req.getProjectCode();
            if (t.projectName == null || t.projectName.isEmpty()) t.projectName = req.getProjectName();
            // 同项目下不允许出现相同表名
            if (metaRepo.existsByProjectCodeAndTableName(t.projectCode, t.tableName)) {
                throw new BizException(400, "导入脚本中项目【" + t.projectCode + "】下表名【" + t.tableName + "】已存在，跳过");
            }

            ProjectTableMeta meta = new ProjectTableMeta();
            meta.setProjectCode(t.projectCode);
            meta.setProjectName(t.projectName);
            meta.setTableName(t.tableName);
            meta.setCreatedBy(userId);
            meta = metaRepo.save(meta);

            for (ParsedField f : t.fields) {
                ProjectTableField field = new ProjectTableField();
                field.setProjectTableId(meta.getId());
                field.setColumnName(f.name);
                field.setColumnType(f.type);
                field.setColumnComment(f.comment);
                field.setBizDesc(f.bizDesc);
                fieldRepo.save(field);
            }
            results.add(detail(meta.getId()));
        }
        return results;
    }

    // ─── 内部解析器（支持简单 CREATE TABLE 脚本） ───

    private static final class ParsedTable {
        String projectCode, projectName, tableName;
        List<ParsedField> fields = new ArrayList<>();
    }

    private static final class ParsedField {
        String name, type, comment, bizDesc;
    }

    private List<ParsedTable> parseScript(String script) {
        // 预处理: 合并被换行打断的字段行（即使 AI 没修好格式也能正确解析）
        String normalized = mergeBrokenFieldLines(script);
        List<ParsedTable> tables = new ArrayList<>();
        String[] lines = normalized.split("\\n");
        ParsedTable current = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // 每张表以 "-- @projectCode:xxx @projectName:xxx @tableName:xxx" 标记开始
            if (line.startsWith("--")) {
                if (line.contains("@projectCode") && line.contains("@tableName")) {
                    current = new ParsedTable();
                    for (String seg : line.substring(2).split("\\s+")) {
                        if (seg.startsWith("@projectCode=")) current.projectCode = seg.substring("@projectCode=".length()).trim();
                        else if (seg.startsWith("@projectName=")) current.projectName = seg.substring("@projectName=".length()).trim();
                        else if (seg.startsWith("@tableName=")) current.tableName = seg.substring("@tableName=".length()).trim();
                    }
                    if (current.projectCode != null && current.tableName != null) {
                        tables.add(current);
                    } else {
                        current = null;
                    }
                }
                continue;
            }

            if (current == null) continue;

            // 跳过 SQL 关键字行: CREATE/INSERT/UPDATE/DELETE/PRIMARY/FOREIGN/UNIQUE/CONSTRAINT/INDEX/REFERENCES 等
            String upper = line.toUpperCase();
            if (upper.startsWith("CREATE") || upper.startsWith("PRIMARY")
                    || upper.startsWith("FOREIGN") || upper.startsWith("UNIQUE")
                    || upper.startsWith("CONSTRAINT") || upper.startsWith("INDEX")
                    || upper.startsWith("REFERENCES") || upper.startsWith("ON ")
                    || upper.startsWith("KEY ") || line.startsWith(")")
                    || line.startsWith("(")) {
                continue;
            }

            // 解析字段行: 字段名 类型 COMMENT '注释'
            // 匹配: column_name VARCHAR2(50) COMMENT 'xxx'
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "^(\\w+)\\s+([\\w\\(\\)\\s,]+?)(\\s+COMMENT\\s+'([^']*)')?\\s*,?$"
            ).matcher(line);
            if (m.find()) {
                ParsedField f = new ParsedField();
                f.name = m.group(1);
                f.type = m.group(2).trim();
                f.comment = m.group(4);
                current.fields.add(f);
            }
        }
        return tables;
    }

    /**
     * 合并被换行打断的字段行。
     * 典型场景：
     *   id  VARCHAR2(50)
     *   COMMENT '用户ID' PRIMARY KEY,
     * 合并为: id  VARCHAR2(50)  COMMENT '用户ID' PRIMARY KEY,
     *
     * 规则:
     *   1. 在 CREATE TABLE 块内,以 "," 或 ");" 作为字段结束标记
     *   2. 当前行是"字段行片段"（有 "," 或 ");" 结尾）则完成一行；否则继续拼下一行
     *   3. 标记行 (-- 开头) / CREATE INDEX / 块外空行 不参与合并
     */
    private String mergeBrokenFieldLines(String script) {
        if (script == null || script.isEmpty()) return script;
        StringBuilder sb = new StringBuilder();
        String[] lines = script.split("\\n", -1);
        boolean inCreateTable = false;
        StringBuilder buffer = new StringBuilder();
        for (String raw : lines) {
            String trimmed = raw.trim();
            // 进入 CREATE TABLE 块
            if (!inCreateTable) {
                if (trimmed.toUpperCase().contains("CREATE TABLE") && trimmed.contains("(")) {
                    inCreateTable = true;
                    // 块开始行
                    if (buffer.length() > 0) { sb.append(buffer).append("\n"); buffer.setLength(0); }
                    sb.append(raw).append("\n");
                    // 同一行可能就结束: "CREATE TABLE x ( id INT );" → 整块在这一行
                    if (trimmed.contains(");")) { inCreateTable = false; }
                    continue;
                } else {
                    sb.append(raw).append("\n");
                    continue;
                }
            }
            // 已在 CREATE TABLE 块内
            if (trimmed.equals(");") || trimmed.startsWith(");")) {
                // 表结束
                if (buffer.length() > 0) { sb.append(buffer).append("\n"); buffer.setLength(0); }
                sb.append(raw).append("\n");
                inCreateTable = false;
                continue;
            }
            // 块内的所有行（除"）;"）都先堆到 buffer
            buffer.append(raw).append(" "); // 用空格连接片段
            // 如果该行以 "," 结尾，说明字段完整，flush
            if (trimmed.endsWith(",")) {
                sb.append(buffer).append("\n");
                buffer.setLength(0);
            }
            // 否则继续累积
        }
        if (buffer.length() > 0) sb.append(buffer).append("\n");
        return sb.toString();
    }

    /**
     * 解析「表结构清单」格式（数据库软件复制结果）
     * 支持两种分隔：
     *   1) 制表符 / 多个空格（Navicat、Workbench 复制）
     *   2) | 列 | 列 |（Markdown 表格）
     * 表头必须包含【列名/字段名】、【类型】、【注释/说明】；【表名】可选
     */
    private List<ParsedTable> parseListScript(String script) {
        List<ParsedTable> tables = new ArrayList<>();
        String[] lines = script.split("\\n");
        List<String> cleaned = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            // 跳过 Markdown 分隔行 |---|---|
            if (line.matches("^\\|?[\\s\\-:|]+\\|?$")) continue;
            cleaned.add(line);
        }
        if (cleaned.isEmpty()) {
            throw new BizException(400, "表结构清单内容为空");
        }

        // 解析表头
        String headerLine = cleaned.get(0);
        String[] headers = splitRow(headerLine);
        int colIdx = -1, typeIdx = -1, commentIdx = -1, tableIdx = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().replaceAll("\\s+", "");
            if (h.contains("列名") || h.contains("字段名") || h.contains("field") || h.contains("column")) colIdx = i;
            else if (h.contains("类型") || h.contains("type")) typeIdx = i;
            else if (h.contains("注释") || h.contains("说明") || h.contains("comment") || h.contains("备注")) commentIdx = i;
            else if (h.contains("表名") || h.contains("table")) tableIdx = i;
        }
        if (colIdx < 0 || typeIdx < 0) {
            throw new BizException(400, "表头需包含【列名】与【类型】两列");
        }

        // 逐行解析
        java.util.Map<String, ParsedTable> tableMap = new java.util.LinkedHashMap<>();
        for (int i = 1; i < cleaned.size(); i++) {
            String[] cols = splitRow(cleaned.get(i));
            if (cols.length <= Math.max(colIdx, typeIdx)) continue;
            String name = cols[colIdx].trim();
            String type = cols[typeIdx].trim();
            if (name.isEmpty() || type.isEmpty()) continue;
            String comment = (commentIdx >= 0 && commentIdx < cols.length) ? cols[commentIdx].trim() : "";
            String tableName = (tableIdx >= 0 && tableIdx < cols.length) ? cols[tableIdx].trim() : "";

            String key = tableName.isEmpty() ? "__default__" : tableName;
            ParsedTable pt = tableMap.computeIfAbsent(key, k -> {
                ParsedTable t = new ParsedTable();
                t.tableName = k.equals("__default__") ? "" : k;
                return t;
            });
            ParsedField f = new ParsedField();
            f.name = name;
            f.type = type;
            f.comment = comment;
            pt.fields.add(f);
        }

        tableMap.forEach((k, v) -> tables.add(v));
        if (tables.isEmpty()) {
            throw new BizException(400, "未能从清单中解析出任何字段，请检查格式");
        }
        return tables;
    }

    /**
     * 拆分一行：兼容 | 分隔（Markdown）与 制表符/多空格 分隔（Navicat 复制）
     */
    private String[] splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        if (s.contains("|")) {
            return s.split("\\|", -1);
        }
        return s.split("\\t|\\s{2,}", -1);
    }

    // ─── AI 自动修复 ───

    /**
     * 调用大模型自动修复脚本格式错误。
     * 返回修复后的脚本文本。
     */
    public String autoFix(AutoFixRequest req) {
        if (req == null || req.getScript() == null || req.getScript().isBlank()) {
            throw new BizException(400, "脚本内容为空");
        }
        String mode = req.getMode() == null ? "ddl" : req.getMode().toLowerCase();

        // 取 skill
        String skillName = "ddl".equals(mode)
                ? "导入脚本修复-DDL模式"
                : "导入脚本修复-表结构清单模式";
        SkillTemplate skill = skillService.getByName(skillName);

        // 取默认模型
        LlmConfig model = llmConfigService.getDefault();

        // 组装 prompt
        String userPrompt = buildAutoFixUserPrompt(req.getScript(), mode, req.getProjectCode(), req.getProjectName());

        String fixed = aiClient.chatWith(model.getId(), skill.getContent(), userPrompt);
        if (fixed == null || fixed.isBlank()) {
            throw new BizException(400, "AI 未返回修复结果，请检查模型配置");
        }
        return fixed;
    }

    private String buildAutoFixUserPrompt(String script, String mode, String projectCode, String projectName) {
        // 顶部填写的项目编号/名称,作为大模型写表名声明行时的真实值（替代字面量 xxx）
        String pc = projectCode == null || projectCode.isBlank() ? "XXX" : projectCode;
        String pn = projectName == null || projectName.isBlank() ? "XXX" : projectName;

        if ("list".equals(mode)) {
            return """
                请将下面的「表结构清单」修复为标准格式后输出。

                【顶部填写的项目信息】
                - 项目编号：""" + pc + """
                - 项目名称：""" + pn + """

                【要求】
                1. 仅输出修复后的脚本，不要任何解释、注释或前缀
                2. 保持列名、类型、注释原文不变，只修复格式问题
                3. 识别并补全缺失的表名（若某字段行未标注表名，以第一行遇到的表名为准）
                4. 支持的分隔符：制表符、多空格（≥2个）、Markdown 表格（|...|）
                5. 表头必须包含：列名（或字段名）、类型（或 type）、注释（或说明）
                6. 允许字段行的表名列缺失（会用顶部填写的项目编号作兜底），但列名和类型不能为空

                【原始脚本】
                """ + script;
        } else {
            return """
                请将下面的「建表语句（DDL）」修复为标准格式后输出。

                【顶部填写的项目信息】（用于表名声明行，禁止使用占位符 xxx）
                - 项目编号：""" + pc + """
                - 项目名称：""" + pn + """

                【要求】
                1. 仅输出修复后的脚本，不要任何解释、注释或前缀
                2. 保持字段名、类型、注释原文不变，只修复格式问题
                3. 每张表的表名声明使用注释格式：-- @projectCode=""" + pc + " @projectName=" + pn + """
                   @tableName=<从 CREATE TABLE 语句中提取的真实表名>
                   （@projectCode/@projectName 必须使用上面【顶部填写的项目信息】中的值，不允许使用 xxx 占位符）
                4. 字段行格式：字段名  类型  COMMENT '注释'
                5. 合并多行布局：如果字段名/类型/COMMENT 被换行打断，请合并为单行
                6. 移除多余的空行、杂乱的注释、-- 索引/-- 注释 等行
                7. 主键约束行（如 PRIMARY KEY (id)）、CREATE INDEX、CREATE UNIQUE INDEX 行直接忽略，不输出

                【原始脚本】
                """ + script;
        }
    }

    // ─── VO 转换 ───

    private ProjectTableMetaVO toVO(ProjectTableMeta meta) {
        List<ProjectTableMetaVO.FieldItemVO> fields = fieldRepo
                .findByProjectTableIdOrderByIdAsc(meta.getId())
                .stream()
                .map(f -> ProjectTableMetaVO.FieldItemVO.builder()
                        .id(f.getId())
                        .columnName(f.getColumnName())
                        .columnType(f.getColumnType())
                        .columnComment(f.getColumnComment())
                        .bizDesc(f.getBizDesc())
                        .build())
                .collect(Collectors.toList());

        return ProjectTableMetaVO.builder()
                .id(meta.getId())
                .projectCode(meta.getProjectCode())
                .projectName(meta.getProjectName())
                .tableName(meta.getTableName())
                .createdAt(meta.getCreatedAt())
                .updatedAt(meta.getUpdatedAt())
                .fields(fields)
                .build();
    }
}
