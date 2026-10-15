# NL2SQL Backend

可视化 NL2SQL 自助查询工具 - 后端

## 技术栈

- Java 21 / Spring Boot 3.3.5
- Spring Data JPA + PostgreSQL 16
- Spring Security + JJWT
- JSqlParser (SQL 安全校验)
- EasyExcel (导出)
- Redis 7 (缓存/会话)

## 快速开始

### 1. 启动依赖

```bash
cd /Users/yang/个人/可视化NL2SQL自助查询工具/backend
docker compose up -d
```

### 2. 配置环境变量

可选 (LLM):
```bash
export LLM_BASE_URL=http://localhost:11434   # Ollama 默认
export LLM_MODEL=qwen2.5-coder:7b
export LLM_API_KEY=any
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

应用监听 `http://localhost:8080`，接口前缀 `/api`

### 4. 默认账号

启动时自动创建演示账号：
- 用户名: `admin`
- 密  码: `admin123`

### 5. 健康检查

```bash
curl http://localhost:8080/api/actuator/health
```

## 模块结构

```
com.nl2sql
├── Nl2sqlApplication        # 主入口
├── common                   # 通用(R / 异常)
├── security                 # JWT + AES
├── auth                     # 用户认证
├── datasource               # 数据源管理
├── meta                     # 元数据(待开发)
├── workspace                # 查询工作台(待开发)
├── ai                       # AI 适配(待开发)
├── parser                   # SQL 校验引擎(待开发)
└── export                   # 导出引擎(待开发)
```

## 已实现接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录（公开） |
| GET  | `/api/datasource/list` | 数据源列表 |
| GET  | `/api/datasource/{id}` | 数据源详情 |
| POST | `/api/datasource/test` | 测试连接 |
| POST | `/api/datasource/save` | 新增/编辑 |
| DELETE | `/api/datasource/{id}` | 删除（仅本系统） |
| POST | `/api/datasource/{id}/refresh-meta` | 刷新元数据 |

## 测试

```bash
mvn test
```
