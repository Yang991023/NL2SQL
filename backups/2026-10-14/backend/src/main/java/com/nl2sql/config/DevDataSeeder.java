package com.nl2sql.config;

import com.nl2sql.audit.entity.OpLog;
import com.nl2sql.audit.repo.OpLogRepository;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.field.entity.FieldBizMeta;
import com.nl2sql.field.repo.FieldBizMetaRepository;
import com.nl2sql.meta.entity.TableMeta;
import com.nl2sql.meta.repo.TableMetaRepository;
import com.nl2sql.model.entity.LlmConfig;
import com.nl2sql.model.repo.LlmConfigRepository;
import com.nl2sql.security.AESUtil;
import com.nl2sql.skill.entity.SkillTemplate;
import com.nl2sql.skill.repo.SkillTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dev Profile 演示数据初始化
 * 仅在 dev 环境下注入,便于本地快速跑通
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final SkillTemplateRepository skillRepo;
    private final LlmConfigRepository llmRepo;
    private final DataSourceRepository dsRepo;
    private final TableMetaRepository tableMetaRepo;
    private final FieldBizMetaRepository fieldBizMetaRepo;
    private final OpLogRepository opLogRepo;
    private final AESUtil aes;

    @Value("${nl2sql.ai.auto-mock:true}")
    private boolean autoMock;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Skill 默认模板 - 查询面板可视化配置生成 SQL
        if (skillRepo.count() == 0) {
            SkillTemplate t = new SkillTemplate();
            t.setName("查询面板生成SQL");
            t.setContent(com.nl2sql.skill.service.SkillTemplateService.DEFAULT_PROMPT);
            t.setDescription("【查询面板生成SQL】根据表结构可视化配置区的字段、业务描述、过滤条件、关联关系生成 SQL。不依赖自然语言描述。");
            t.setIsDefault(false);
            t.setEnabled(true);
            skillRepo.save(t);
            log.info("[Dev Seed] Skill [查询面板生成SQL] 已注入 (id=1)");

            SkillTemplate t2 = new SkillTemplate();
            t2.setName("自然语言描述生成SQL");
            t2.setContent(com.nl2sql.skill.service.SkillTemplateService.NL_ONLY_PROMPT);
            t2.setDescription("【自然语言描述生成SQL】根据自然语言描述 + 已选表的表结构生成 SQL。不依赖可视化配置区的过滤条件和关联。");
            t2.setIsDefault(true);  // 设默认，供 NL 按钮使用
            t2.setEnabled(true);
            skillRepo.save(t2);
            log.info("[Dev Seed] Skill [自然语言描述生成SQL] 已注入 (id=2)");

            // 3. 导入脚本修复 - DDL 模式
            SkillTemplate t3 = new SkillTemplate();
            t3.setName("导入脚本修复-DDL模式");
            t3.setContent("""
                # 角色
                你是一个专业的 DDL 脚本格式化与修复专家。用户会提供一段可能存在格式问题的 CREATE TABLE 建表语句，
                你的任务是对其进行规范化修复，并输出修复后的标准 DDL 脚本。

                # 入库字段说明（后端解析器会提取这些字段）
                每个字段对应入库的一个字段，字段之间用**空格**或**制表符**分隔：
                  字段名   类型   COMMENT '注释'
                例如：
                  id        VARCHAR2(50)   COMMENT '用户ID'
                  order_no  VARCHAR2(32)   COMMENT '订单编号'
                  amount    DECIMAL(12,2)  COMMENT '订单金额'

                # 你的能力
                1. 识别并补全缺失的表名声明注释：每张表开头必须有 "-- @projectCode=xxx @projectName=xxx @tableName=xxx" 声明行
                   - projectCode / projectName 来自用户在系统顶部填写的值，原文不变，直接填充进声明行
                   - tableName 优先取 CREATE TABLE 语句中的表名；若有多张表，必须按顺序分别为每张表写声明行
                2. 修正字段行格式：统一为 "字段名  类型  COMMENT '注释'" 或 "字段名  类型"（无注释时）
                3. 识别并正确处理字段行：
                   - 包含 NOT NULL / PRIMARY KEY / UNIQUE / DEFAULT xxx 等约束的关键字应视为类型的一部分或紧跟类型后
                   - 主键约束行如 "PRIMARY KEY (id)" 直接忽略，不输出
                   - 括号开头的行如 "( id VARCHAR..." 中的字段名应从括号内提取
                   - 结尾的 ");" 直接忽略
                4. 移除乱码、空行、无效注释（如 "-- ENGINE=InnoDB"）
                5. 保持字段名、类型、注释原文不变，不做语义修改

                # 输入脚本示例（常见问题）
                输入可能包含以下问题，你的任务是自动修正：
                - 问题1：缺少 @tableName 声明行
                - 问题2：字段行格式混乱，如 "id VARCHAR2(50)PRIMARY KEY"（约束紧贴类型）
                - 问题3：字段行以 "  id" 前导空格开头
                - 问题4：字段行的类型中包含多个空格或缺少空格，如 "id  VARCHAR2(50)"
                - 问题5：多表脚本缺少空行分隔

                # 输出要求（极为重要）
                1. **仅输出修复后的 DDL 脚本，不要任何解释、任何中文、任何 markdown 围栏**
                2. 每张表的表名声明行必须紧跟在该表的 CREATE TABLE 语句正上方（不允许在其他位置）
                3. 保持原有字段的顺序，不增不减
                4. 每张表以 ");" 结尾，表之间用一个空行分隔
                5. 声明行格式：-- @projectCode=XXX @projectName=XXX @tableName=XXX
                   （projectCode/projectName 与用户在系统顶部填写的完全一致，大小写敏感）
                """);
            t3.setDescription("【导入脚本修复-DDL模式】将格式错误的 CREATE TABLE 语句修复为标准格式，补全表名声明，输出可直接导入的 DDL 脚本。");
            t3.setIsDefault(false);
            t3.setEnabled(true);
            skillRepo.save(t3);
            log.info("[Dev Seed] Skill [导入脚本修复-DDL模式] 已注入 (id=3)");

            // 4. 导入脚本修复 - 表结构清单模式
            SkillTemplate t4 = new SkillTemplate();
            t4.setName("导入脚本修复-表结构清单模式");
            t4.setContent("""
                # 角色
                你是一个专业的表结构清单格式化与修复专家。用户会提供一段从 Navicat、DBeaver、DataGrip、MySQL Workbench 等数据库软件
                复制的「表结构清单」，可能存在格式混乱、分隔符不一致、表名缺失等问题。
                你的任务是对其进行规范化修复，并输出可直接导入的清单格式。

                # 入库格式说明（极为重要）
                后端解析器按以下规则提取字段：
                1. 自动识别分隔符：制表符（\\t）、多个空格（≥2个）、Markdown 表格（|...|）均有效
                2. 识别表头行并自动过滤：包含"列名"、"字段名"、"类型"、"注释"、"说明"、"comment"、"type"、"field"、"column"等关键词的行视为表头，不输出
                3. 字段对应关系（按出现顺序映射到列）：
                   - 第1列 → 字段名（columnName）
                   - 第2列 → 字段类型（columnType）
                   - 第3列 → 注释（columnComment，可空）
                   - 第4列 → 表名（tableName，可空；空则用第一行遇到的表名兜底）
                4. 表名列可以缺失，但字段名列和类型列必须有实际内容

                # 你的能力
                1. 统一分隔符：将混用的制表符、空格、| 分隔全部统一为**双空格分隔**（两个空格）
                2. 识别并过滤表头行：表头行（如"列名  类型  注释  表名" / "Field  Type  Comment  Table"）不输出
                3. 识别并补全缺失的表名：
                   - 若某字段行末尾表名列缺失，用该字段所属表的第一行表名兜底
                   - 若整张表都没有表名，则用 "-- 无表名" 标注
                4. 识别字段的列位置：列名始终在第1列，类型在第2列，注释在第3列，表名在第4列
                5. 过滤掉空行、全是横线的 Markdown 分隔行（|---|---|）
                6. 保持列名、类型、注释原文不变，不做语义修改

                # 输入脚本常见问题（你的任务）
                - 问题1：混用多种分隔符（tab、空格、|），导致列位置错乱
                - 问题2：第1列（列名）或第2列（类型）有空值或被省略
                - 问题3：表头行未过滤，用户误复制了列标题行
                - 问题4：表名列全部缺失，需要从上下文推断表名
                - 问题5：同一张表的字段分散在多段，中间有间隔行
                - 问题6：字段值中包含特殊字符（括号、引号），需要保留
                - 问题7：MySQL/Oracle 导出的类型带长度信息如 VARCHAR(50)，应原样保留

                # 输出要求（极为重要）
                1. **仅输出修复后的表结构清单，不要任何解释、任何中文、任何 markdown 围栏**
                2. 统一用双空格分隔每列：列名  类型  注释  表名
                3. 每张表的字段连续排列；表名列（最后一列）不得为空，空则填入该表所属表名
                4. 表头行不输出
                5. 空行不输出
                6. 多表时，不同表的字段之间用空行分隔（空行表示换表）
                """);
            t4.setDescription("【导入脚本修复-表结构清单模式】将格式混乱的数据库软件表结构清单修复为标准格式，补全表名，输出可直接导入的清单格式。");
            t4.setIsDefault(false);
            t4.setEnabled(true);
            skillRepo.save(t4);
            log.info("[Dev Seed] Skill [导入脚本修复-表结构清单模式] 已注入 (id=4)");
        }

        // 5. Mock LLM
        if (autoMock && llmRepo.count() == 0) {
            LlmConfig c = new LlmConfig();
            c.setName("Mock (本地调试)");
            c.setBaseUrl("mock://local");
            c.setApiKeyEnc(aes.encrypt("mock-key"));
            c.setModel("mock-sql-generator");
            c.setTimeoutS(5);
            c.setEnabled(true);
            c.setIsDefault(true);
            llmRepo.save(c);
            log.info("[Dev Seed] Mock LLM 已注入 (id=1)");
        }

        // 6. 演示数据源(无 password,只用于接口调用,实际连接会失败,但元数据查询会返回空)
        if (dsRepo.count() == 0) {
            DataSource demo = new DataSource();
            demo.setName("Demo MySQL (本地演示)");
            demo.setDbType("MYSQL");
            demo.setHost("127.0.0.1");
            demo.setPort(3306);
            demo.setDatabaseName("demo");
            demo.setUsername("root");
            demo.setPasswordEnc(aes.encrypt("demo"));
            demo.setRemark("内置演示数据源(元数据为空,可手动触发元数据同步)");
            dsRepo.save(demo);
            log.info("[Dev Seed] 演示数据源已注入 (id=1)");
        }

        // 7. 演示元数据(可直接在前端"元数据"页可见)
        if (tableMetaRepo.count() == 0) {
            try {
                TableMeta tm = new TableMeta();
                tm.setDataSourceId(1L);
                tm.setDatabaseName("demo");
                tm.setTableName("users");
                tm.setTableComment("用户表(演示)");
                tm.setColumnsJson("""
                        [
                          {"name":"id","type":"BIGINT","isPk":true,"nullable":false,"comment":"主键"},
                          {"name":"name","type":"VARCHAR(100)","nullable":false,"comment":"姓名"},
                          {"name":"email","type":"VARCHAR(200)","nullable":true,"comment":"邮箱"},
                          {"name":"status","type":"TINYINT","nullable":false,"comment":"状态: 0-禁用 1-启用"},
                          {"name":"created_at","type":"DATETIME","nullable":false,"comment":"创建时间"}
                        ]
                        """);
                tableMetaRepo.save(tm);

                TableMeta tm2 = new TableMeta();
                tm2.setDataSourceId(1L);
                tm2.setDatabaseName("demo");
                tm2.setTableName("orders");
                tm2.setTableComment("订单表(演示)");
                tm2.setColumnsJson("""
                        [
                          {"name":"id","type":"BIGINT","isPk":true,"nullable":false,"comment":"主键"},
                          {"name":"user_id","type":"BIGINT","nullable":false,"comment":"用户ID"},
                          {"name":"amount","type":"DECIMAL(10,2)","nullable":false,"comment":"金额"},
                          {"name":"status","type":"TINYINT","nullable":false,"comment":"订单状态"},
                          {"name":"created_at","type":"DATETIME","nullable":false,"comment":"下单时间"}
                        ]
                        """);
                tableMetaRepo.save(tm2);
                log.info("[Dev Seed] 演示表元数据已注入 (users/orders)");
            } catch (Exception e) {
                log.warn("[Dev Seed] 元数据 seed 失败(可忽略): {}", e.getMessage());
            }
        }

        // 8. 演示字段业务元数据
        if (fieldBizMetaRepo.count() == 0) {
            try {
                FieldBizMeta fb = new FieldBizMeta();
                fb.setDataSourceId(1L);
                fb.setDatabaseName("demo");
                fb.setTableName("users");
                fb.setColumnName("status");
                fb.setBizDesc("用户状态:0=禁用 1=启用,默认 1");
                fb.setFilterLogic("EQUAL");
                fb.setFilterValue("1");
                fieldBizMetaRepo.save(fb);
                log.info("[Dev Seed] 演示字段业务元数据已注入");
            } catch (Exception ignore) {}
        }

        // 9. 演示审计
        if (opLogRepo.count() == 0) {
            OpLog o = new OpLog();
            o.setUserId(1L);
            o.setAction("SYSTEM_INIT");
            o.setTargetType("system");
            o.setTargetId(0L);
            o.setDetail("系统初始化");
            o.setResult("SUCCESS");
            opLogRepo.save(o);
            log.info("[Dev Seed] 演示审计已注入");
        }

        log.info("[Dev Seed] 完成。可调用 /api/auth/login(admin/admin123) 体验。");
    }
}
