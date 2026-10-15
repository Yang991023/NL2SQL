# 2026-10-14 变更归档

> 本次会话的代码变更,按"原文件 → 修改后文件"的方式备份。
> 原文件在 `backups/2026-10-14/` 下,保留完整目录结构。

## 涉及文件清单

| # | 文件 | 主要改动 |
|---|---|---|
| 1 | `backend/src/main/java/com/nl2sql/config/DevDataSeeder.java` | 优化两个 Skill (DDL 模式 / 表结构清单模式) 的 Prompt:新增"入库字段说明"、"输入常见问题"、"输出要求"等,提高大模型识别准确率 |
| 2 | `backend/src/main/java/com/nl2sql/project/service/ProjectTableMetaService.java` | ① 去掉新增/导入时 `projectCode`/`projectName` 重复校验,保留同项目下表名重复校验;② 新增 `mergeBrokenFieldLines()` 合并多行字段布局;③ `buildAutoFixUserPrompt` 注入 `projectCode/projectName` 真实值,禁止大模型输出 `xxx` 占位符;④ `autoFix()` 调用 prompt 改造 |
| 3 | `backend/src/main/java/com/nl2sql/project/dto/AutoFixRequest.java` | 新增 `projectCode`/`projectName` 字段 |
| 4 | `backend/src/main/java/com/nl2sql/ai/dto/AiDtos.java` | `ChatMessage` 新增 `reasoningContent` 字段(DeepSeek 推理模型兼容) |
| 5 | `backend/src/main/java/com/nl2sql/ai/service/AiClient.java` | 读取响应时若 `content` 为空,fallback 到 `reasoning_content`(修复 deepseek-flash 推理模型返回空的问题) |
| 6 | `frontend/src/api/projectTable.ts` | `autoFix()` API 增加 `projectCode/projectName` 参数 |
| 7 | `frontend/src/pages/ProjectTableMeta.tsx` | `onAutoFix` 调用时把顶部填写的 `importProjectCode/importProjectName` 传给后端 |

## 数据库变更

通过 SQL 直接更新 (无需重启,直接生效):
- `sys_.t_skill_template` id=4 (DDL 模式) - 升级 Skill Prompt
- `sys_.t_skill_template` id=5 (表结构清单模式) - 升级 Skill Prompt

## 编译状态

- 后端: `mvn -DskipTests package` ✅ 通过
- 前端: `tsc --noEmit` ✅ 通过

## 待用户操作

- 在 27 终端重启后端进程: `java -jar target/nl2sql-backend-1.3.0.jar` (新代码生效)
- 前端 Vite HMR 已自动应用,无需手动刷新
