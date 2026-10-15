# 可视化 NL2SQL 自助查询工具

> V1.3 完整版 - 内网私有化部署 / 小团队级

## 一句话目标

让**不懂 SQL 的业务/运营/产品人员**通过可视化 + 自然语言安全查询数据库，全程只读。

## 文档导航

| 文档 | 说明 |
|---|---|
| [需求说明书](./可视化NL2SQL自助查询工具需求说明书.md) | 原始需求 V1.3 |
| [技术方案](./docs/技术方案.md) | 技术选型 + 架构设计 |
| [架构图](./docs/架构图.md) | 部署架构 / 模块 / SQL 校验 / 时序 / ER |
| [MVP 任务清单](./docs/MVP任务清单.md) | 分阶段交付清单 |
| [启动流程](./docs/启动流程.md) | 三种模式: Demo / 本地开发 / 生产部署 |
| [后端说明](./backend/README.md) | Spring Boot 工程 |
| [前端说明](./frontend/README.md) | React 工程 |

## 快速启动

```bash
# 1. 启动数据库/缓存
cd backend && docker compose up -d

# 2. 启动后端
mvn spring-boot:run

# 3. 启动前端 (新窗口)
cd ../frontend && npm install && npm run dev
```

打开 http://localhost:5173，账号 `admin / admin123`

## 当前进度

- ✅ 文档（需求 / 方案 / 架构图）
- ✅ 工程脚手架（前后端）
- ✅ 认证（JWT）
- ✅ 数据源 CRUD（含测试连接）
- ⏳ 元数据自动同步
- ⏳ 查询工作台（画布/字段/自然语言/AI 生成/校验/执行/结果展示）
- ⏳ 大模型 / Skill 模板配置
- ⏳ 查询结果导出 / 表结构导出
- ⏳ SQL 安全校验引擎
