# springai-knowledge-server

基于 Spring Boot 4 + Spring AI + PostgreSQL(pgvector) 的企业知识库后端，提供文档管理、RAG 问答、RBAC 权限控制、问答日志与反馈等能力。

## 功能概览

- JWT 登录鉴权，基于角色与权限（RBAC）控制接口访问
- 文档管理：支持文本录入、`PDF/TXT` 上传、替换文件、重建索引
- 检索增强：向量检索 + PostgreSQL 全文检索混合召回
- 可选重排：调用 SiliconFlow `rerank` 接口二次排序
- 问答能力：基于知识片段构建上下文，调用大模型生成答案
- 可观测与管理：操作日志、问答日志、反馈记录、Actuator 健康检查
- 动态配置：系统参数持久化到数据库并支持在线刷新
- API 文档：集成 Swagger UI / OpenAPI

## 技术栈

- Java 21
- Spring Boot 4.0.3
- Spring Security + JWT (jjwt)
- Spring Data JPA
- Spring AI 2.0.0-M2
- PostgreSQL + pgvector
- springdoc-openapi (Swagger UI)

## 运行前准备

1. 安装并启动 PostgreSQL（建议 15+）
2. 创建数据库并启用 `pgvector` 扩展

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

3. 准备 Java 21 环境

## 环境变量

`application.yml` 中使用了以下环境变量（至少需要前三项）：

| 变量名 | 必填 | 说明 |
| --- | --- | --- |
| `PG_PASSWORD` | 是 | PostgreSQL 密码（默认用户名 `postgres`） |
| `SILICONFLOW_API_KEY` | 是 | 模型与重排服务 API Key |
| `RABBITMQ_PASSWORD` | 是 | RabbitMQ 密码占位符（当前代码未使用 MQ 业务，但建议提供） |
| `SECURITY_JWT_SECRET` | 否 | JWT 密钥，至少 32 位；默认值仅供开发环境 |
| `APP_ADMIN_USERNAME` | 否 | 初始化管理员用户名，默认 `admin` |
| `APP_ADMIN_PASSWORD` | 否 | 初始化管理员密码，默认 `admin123` |

PowerShell 示例：

```powershell
$env:PG_PASSWORD="your_postgres_password"
$env:SILICONFLOW_API_KEY="your_siliconflow_key"
$env:RABBITMQ_PASSWORD="your_rabbitmq_password"
$env:SECURITY_JWT_SECRET="replace-with-at-least-32-characters-secret"
```

Bash 示例：

```bash
export PG_PASSWORD="your_postgres_password"
export SILICONFLOW_API_KEY="your_siliconflow_key"
export RABBITMQ_PASSWORD="your_rabbitmq_password"
export SECURITY_JWT_SECRET="replace-with-at-least-32-characters-secret"
```

## 启动项目

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

默认访问地址：

- 服务基地址：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

## 初始化数据

应用首次启动会自动创建：

- 角色：`ADMIN`、`USER`
- 管理员账号：`admin / admin123`（可通过 `APP_ADMIN_*` 覆盖）

默认权限（简化说明）：

- `ADMIN`：用户/角色/文档/配置/日志/反馈读写权限
- `USER`：`DOC_READ`、`FEEDBACK_WRITE`

## 鉴权方式

除以下接口外，其他接口都需要 JWT：

- `POST /api/auth/login`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`
- `GET /actuator/health`

登录获取 Token：

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

后续请求在 Header 中携带：

```text
Authorization: Bearer <your_jwt_token>
```

## 关键接口

完整参数与响应请以 Swagger 为准，这里给出高频接口：

| 模块 | 方法 | 路径 | 权限 |
| --- | --- | --- | --- |
| 认证 | `POST` | `/api/auth/login` | 公开 |
| 认证 | `POST` | `/api/auth/logout` | 已登录 |
| 用户 | `GET` | `/api/users/me` | 已登录 |
| 用户 | `PATCH` | `/api/users/me` | 已登录 |
| 文档 | `POST` | `/api/documents/upload` | `DOC_WRITE` |
| 文档 | `POST` | `/api/documents` | `DOC_WRITE` |
| 文档 | `POST` | `/api/documents/search` | `DOC_READ` |
| 文档 | `POST` | `/api/documents/{id}/reindex` | `DOC_WRITE` |
| 问答 | `POST` | `/api/qa` | `DOC_READ` |
| 反馈 | `POST` | `/api/feedback` | `FEEDBACK_WRITE` |
| 角色管理 | `GET/POST/PATCH` | `/api/roles...` | `ROLE_READ/ROLE_WRITE` |
| 配置管理 | `GET/PUT` | `/api/config...` | `CONFIG_READ/CONFIG_WRITE` |
| 日志 | `GET` | `/api/logs/operations` | `LOG_READ` |
| 日志 | `GET` | `/api/logs/qa` | `LOG_READ` |

## 常用调用示例

上传文档（`allowedRoles` 支持逗号分隔或多值）：

```bash
curl -X POST "http://localhost:8080/api/documents/upload" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -F "file=@./demo.txt" \
  -F "title=示例文档" \
  -F "allowedRoles=ADMIN,USER"
```

发起问答：

```bash
curl -X POST "http://localhost:8080/api/qa" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"question":"这个系统的核心能力是什么？"}'
```

提交反馈（`qaLogId` 来自 `/api/qa` 返回）：

```bash
curl -X POST "http://localhost:8080/api/feedback" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"qaLogId":1,"helpful":true,"comment":"回答准确"}'
```

## 动态配置（存储于数据库）

系统会在启动时初始化一组配置键，可通过 `/api/config` 在线查看和修改。常用键包括：

- 文档切分：`chunk.size`、`chunk.overlap`、`chunk.embeddingSafeSize`
- 混合检索：`hybrid.topK`、`hybrid.vectorTopK`、`hybrid.keywordTopK`、`hybrid.vectorWeight`、`hybrid.keywordWeight`
- 关键词检索：`keyword.tsConfig`
- RAG 生成：`rag.answerStyle`、`rag.maxAnswerChars`、`rag.maxOutputTokens`、`rag.temperature`、`rag.topP`
- Prompt 模板：`rag.prompt.system`、`rag.prompt.user`

修改后可调用 `POST /api/config/refresh` 触发配置缓存刷新。

## 打包运行

```bash
./mvnw clean package -DskipTests
java -jar target/springai-knowledge-server-0.0.1.jar
```

## 常见问题

- 启动报错 `Could not resolve placeholder ...`：检查环境变量是否已设置
- 启动报错 `JWT secret must be at least 32 characters`：设置 `SECURITY_JWT_SECRET` 且长度 >= 32
- 向量相关报错（如 `type "vector" does not exist`）：确认数据库已执行 `CREATE EXTENSION vector`
- 文件上传失败：仅支持 `PDF/TXT`，默认最大 50MB

