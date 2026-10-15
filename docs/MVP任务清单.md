# MVP 任务清单 (V1.3 完整版)

> 分 5 个 Sprint 交付,每个 Sprint 1-2 周,总计约 8 周
> 状态图例: ⬜ 待开始 / 🟡 进行中 / ✅ 已完成 / 🚧 部分完成

---

## Sprint 0 - 工程基线 ✅ 已完成

- [x] 后端 Spring Boot 工程初始化 (JDK 21)
- [x] 前端 Vite + React 工程初始化
- [x] Docker Compose (PG + Redis)
- [x] 统一返回 R / 全局异常 / JWT 工具 / AES 工具
- [x] 认证模块 (登录、admin 种子账号)
- [x] 数据源 CRUD (含测试连接、删除仅本系统)
- [x] 项目 README 文档

---

## Sprint 1 - 数据源 & 元数据 (核心基础)

> Sprint 1 ✅ 已完成基础 (本轮实现)
- [x] 数据库/表/字段元数据实体 (`t_database`, `t_table_meta`)
- [x] 数据库方言适配器 (`DbDialect`)
  - [x] MySQL 方言 ✅
  - [x] PostgreSQL 方言 ✅
  - [x] Oracle 方言 ✅
- [x] `DialectRouter` 根据数据源类型路由
- [x] `MetaSyncService` 全量同步流程 (列库→列表→描述表→入库)
- [x] `MetaController` 接口 (databases/tables/table/sync)
- [x] 异步同步触发 (新增/手动刷新)
- [x] 前端左侧树组件 `<WorkspacePage>`
  - [x] 数据源→数据库→表 三级懒加载
  - [x] 右键菜单 (数据库详情/刷新元数据/删除)
  - [x] 数据库详情抽屉
  - [x] 表详情弹窗 (字段表格)
- [x] 集成测试 (DbDialectTest)
- [ ] 前端数据源列表页 (待迁入 Workspace)

---

## Sprint 2 - SQL 安全校验引擎 (核心防御)

> 目标: 防 SQL 注入/写入/绕过,所有后续 SQL 操作必经此关

### 后端
- [ ] **SQL 解析封装** `nl2sql-parser`
  - [ ] JSqlParser 集成
  - [ ] 方言切换 (MySqlSqlParser / PostgreSqlParser / OracleParser)
- [ ] **校验服务** `SqlSecurityService`
  - [ ] Layer 1 - 预处理 (去注释、空行、统一空白)
  - [ ] Layer 2 - 语法解析(失败返回 REJECT)
  - [ ] Layer 3 - 语句类型拦截 (黑名单 INSERT/UPDATE/DELETE/...)
  - [ ] Layer 4 - 方言匹配
  - [ ] Layer 5 - 对象存在性校验 (Table/Column 比对元数据)
  - [ ] Layer 6 - 高危逻辑 (笛卡尔积/全表扫描/无 WHERE)
  - [ ] 自动修复 (追加 LIMIT 1000 / 移除尾随分号)
- [ ] **执行超时控制**
  - [ ] HikariCP `connectionTimeout` 5s
  - [ ] Statement timeout 15s (SET STATEMENT_TIMEOUT)
  - [ ] ResultSet max rows 10000
- [ ] **接口**
  - [ ] `POST /api/sql/check` 只检查 (公共入口)
  - [ ] `POST /api/sql/exec` 执行 (内部调用 check,共用引擎)
- [ ] **风险模型**
  - [ ] 风险分项 (REJECT/WARNING/PASS) 状态机
  - [ ] 自动修复 SQL 回写 (fixedSql 字段)

### 前端
- [ ] **检查面板** `<CheckResult>`
  - [ ] 分级样式 (错误红/警告黄/通过绿)
  - [ ] 详细问题列表
  - [ ] "查看修复后 SQL" 按钮
- [ ] **执行流程**
  - [ ] WARNING 弹窗确认
  - [ ] REJECT 直接阻断
  - [ ] 加载中状态

### 测试
- [ ] 单元测试: 各攻击向量 (UNION 注入 / DROP / 注释绕过)
- [ ] 单元测试: 合法 SELECT 通过
- [ ] 单元测试: 方言不匹配 REJECT

---

## Sprint 3 - 查询工作台 + AI

> 目标: 用户可在画布自助构建查询,自然语言生成 SQL

### 后端
- [ ] **大模型客户端** `nl2sql-ai`
  - [ ] OpenAI 兼容客户端 (base_url/api_key/model)
  - [ ] 流式 + 非流式两种返回
  - [ ] 调用超时 / 重试 / 异常降级
- [ ] **大模型配置** (CRUD)
  - [ ] `t_llm_config` 实体
  - [ ] `GET /api/model/list` `POST /api/model/save` `DELETE /api/model/{id}`
  - [ ] 默认模型切换
- [ ] **Skill 模板**
  - [ ] `t_skill_template` 实体
  - [ ] 内置默认模板 (含 {{table_list}} {{table_meta}} {{filter_condition}} {{join_condition}} {{user_nl_query}})
  - [ ] 模板 CRUD + 变量提取
- [ ] **画布状态**
  - [ ] `t_query_canvas` 实体 (JSON 存储)
  - [ ] 画布 CRUD
- [ ] **字段业务元数据**
  - [ ] `t_field_biz_meta` (字段描述/查询逻辑/值)
  - [ ] CRUD + 批量保存
- [ ] **SQL 生成服务** `SqlGenerateService`
  - [ ] 拼装 Prompt (画布 + 模板)
  - [ ] 调用 AI 拿 SQL
  - [ ] 生成结果后置安全校验 (即便 AI 生成的也校验)
- [ ] **接口**
  - [ ] `POST /api/workspace/generate-sql` AI 生成 SQL
  - [ ] `POST /api/workspace/save-canvas` 保存画布
  - [ ] `POST /api/workspace/field-biz-meta/save` 保存字段业务元数据

### 前端
- [ ] **查询工作台主页面** `<Workspace>`
  - [ ] 左树右内容布局
  - [ ] 顶部按钮区 [添加表][生成SQL][清空画布][导出表结构]
- [ ] **表卡片** `<TableCard>`
  - [ ] 字段横向排列
  - [ ] 字段描述/查询逻辑/值输入
  - [ ] 关联字段下拉
- [ ] **自然语言描述区** `<NLInput>`
- [ ] **SQL 编辑区**
  - [ ] Monaco Editor (语法高亮、行号)
  - [ ] [检查][执行SQL] 按钮
  - [ ] 一键复制
- [ ] **结果展示区** `<ResultTable>`
  - [ ] Ant Design Table
  - [ ] 加载/空/错误状态
  - [ ] 列头展示字段业务描述
- [ ] **大模型配置页**
- [ ] **Skill 模板配置页**

### 测试
- [ ] AI 调用 mock 测试
- [ ] 画布保存 / 恢复
- [ ] 字段元数据 CRUD

---

## Sprint 4 - 导出能力

> 目标: 查询结果导出 + 表结构导出

### 后端
- [ ] **导出服务** `ExportService`
  - [ ] EasyExcel 流式写入
  - [ ] Excel/CSV 双格式
  - [ ] 文件名规范 `[数据源_数据库_时间_随机码]`
  - [ ] 异步任务 + 进度查询
  - [ ] 超时 30s / 最大 10w 行限制
- [ ] **表结构导出**
  - [ ] 整合 `t_table_meta` (原生) + `t_field_biz_meta` (用户自定义)
  - [ ] 多表汇总到单个 Excel
- [ ] **导出日志**
  - [ ] `t_op_log` 写入 (用户/时间/数据源/条数/格式)
- [ ] **接口**
  - [ ] `POST /api/export/query-result` 下载查询结果
  - [ ] `POST /api/export/table-structure` 下载表结构

### 前端
- [ ] **导出配置弹窗**
  - [ ] 选择格式 (xlsx/csv)
  - [ ] 自定义文件名
  - [ ] 进度条/成功提示
- [ ] **结果展示区 [导出数据] 按钮** (有数据时亮起)
- [ ] **顶部 [导出表结构] 按钮** (有表时亮起)

### 测试
- [ ] 10w 行导出耗时 < 30s
- [ ] Excel/CSV 用 Office/WPS 可正常打开
- [ ] 超大数据量拦截

---

## Sprint 5 - 高级特性 & 收尾

- [ ] **操作审计** `t_op_log`
  - [ ] AOP 自动记录 (登录/查询/导出/数据源变更)
  - [ ] 管理端查询页面 (高级菜单)
- [ ] **缓存优化**
  - [ ] 元数据 Caffeine + Redis 二级缓存
  - [ ] SQL 检查结果短时缓存 (SQL hash → 校验结果)
- [ ] **限流**
  - [ ] 单用户查询频次限制 (Redis 计数器)
- [ ] **限流 / 防滥用**
  - [ ] 全局限流 (令牌桶)
- [ ] **菜单权限 (若决定加 RBAC)**
  - [ ] 用户/角色/菜单表
  - [ ] 注解拦截
- [ ] **部署文档**
  - [ ] docker-compose 一键启动全栈
  - [ ] Nginx conf 模板
  - [ ] 内网部署 checklist
- [ ] **README 完善**
  - [ ] 截图
  - [ ] FAQ
- [ ] **性能压测**
  - [ ] 元数据加载 ≤ 2s
  - [ ] SQL 生成 ≤ 5s
  - [ ] 10w 行导出 ≤ 30s
- [ ] **安全审计**
  - [ ] 威胁模型文档
  - [ ] SQL 注入向量清单 + 测试通过

---

## 关键里程碑

| Sprint | 里程碑 | 演示能力 |
|---|---|---|
| **S1** 末 | 元数据自动同步 | 新增数据源,左侧树自动出库表 |
| **S2** 末 | SQL 安全校验 | 双引擎 (检查/执行) 共用,无法绕过 |
| **S3** 末 | 端到端查询 | 自然语言 → SQL → 执行 → 结果表格 |
| **S4** 末 | 导出能力 | 结果导出 + 表结构导出 |
| **S5** 末 | 完整验收 | 全需求 + 性能 + 部署 |

---

## 风险点 (优先级追踪)

- [ ] **JSqlParser 对 Oracle 方言支持较弱** — Sprint 2 验证,若不够用,考虑 fallback 到手动正则
- [ ] **AI 生成 SQL 质量** — Sprint 3 选好 prompt 模板,准备 fallback (模板生成 + AI 双路)
- [ ] **目标库慢查询** — Sprint 2 全程 timeout 严格,生产环境要求运维配合
- [ ] **元数据大库拉取超时** — Sprint 1 异步 + 分批,前端展示进度
