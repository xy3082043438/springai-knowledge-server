# springai-knowledge-server

基于 Spring Boot 4、Spring AI、PostgreSQL `pgvector` 的企业知识库后端。当前项目已经实现 RBAC 权限控制、文档入库与切片、混合检索、可选重排、RAG 问答、操作日志、问答日志、反馈记录、系统配置热刷新和 Swagger/OpenAPI 文档。

## 项目能力

- JWT 无状态鉴权，`logout` 通过递增 `tokenVersion` 让旧 Token 失效
- 用户、角色、权限管理
- 文档管理：文本录入、`PDF/TXT` 上传、替换文件、更新内容、删除、单个或全量重建索引
- 文档按角色控制可见范围，支持命中文档片段预览
- 混合检索：向量检索 + PostgreSQL 全文检索加权融合
- 可选调用 SiliconFlow `rerank` 接口进行二次排序，失败时自动回退原始排序
- RAG 问答返回答案、命中文档、命中片段和 `qaLogId`
- 操作日志、问答日志、反馈记录分页查询
- 系统配置持久化到数据库，支持在线刷新缓存
- 提供系统边界说明、系统状态接口、Actuator 健康检查、Swagger UI

## 技术栈

- Java 21
- Spring Boot 4.0.3
- Spring AI 2.0.0-M2
- Spring Security + JWT (`jjwt`)
- Spring Data JPA
- PostgreSQL + `pgvector`
- PDFBox
- springdoc-openapi

## 检索与问答链路

1. 文档内容按块切分并写入 `app_document_chunk` 与 `pgvector` 向量表。
2. 查询时先做向量检索，再做 PostgreSQL 全文检索。
3. 两路结果按配置权重融合排序。
4. 若开启 `app.rerank.enabled=true`，再调用 SiliconFlow `rerank` 二次排序。
5. 将命中片段拼成上下文后调用 OpenAI 兼容接口生成答案。
6. 返回 `answer`、`documents`、`sources`、`qaLogId`，同时落库问答日志。

默认模型配置：

- Chat: `Qwen/Qwen3-14B`
- Embedding: `BAAI/bge-large-zh-v1.5`
- Rerank: `BAAI/bge-reranker-v2-m3`

## 运行前准备

1. 安装 Java 21
2. 安装 PostgreSQL 15+，创建数据库并启用 `vector` 扩展
3. 准备 SiliconFlow API Key

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

## 环境变量与常用配置

以下变量建议在启动前设置：

| 变量名 | 是否建议提供 | 说明 |
| --- | --- | --- |
| `PG_PASSWORD` | 是 | PostgreSQL 密码，默认用户名为 `postgres` |
| `SILICONFLOW_API_KEY` | 是 | Chat / Embedding / Rerank 共用的 API Key |
| `RABBITMQ_PASSWORD` | 是 | 当前没有 MQ 业务逻辑，但 `application.yml` 仍引用该占位符；不设置会导致启动解析失败 |
| `SECURITY_JWT_SECRET` | 强烈建议 | JWT 密钥，长度至少 32；默认值仅适合本地开发 |
| `APP_ADMIN_USERNAME` | 可选 | 初始化管理员用户名，默认 `admin` |
| `APP_ADMIN_PASSWORD` | 可选 | 初始化管理员密码，默认 `admin123` |
| `APP_DOCUMENT_STORAGE_PATH` | 可选 | 上传文件落盘目录，默认 `./data/documents` |
| `APP_RERANK_ENABLED` | 可选 | 是否启用重排，默认 `true` |

PowerShell:

```powershell
$env:PG_PASSWORD="your_postgres_password"
$env:SILICONFLOW_API_KEY="your_siliconflow_key"
$env:RABBITMQ_PASSWORD="placeholder"
$env:SECURITY_JWT_SECRET="replace-with-at-least-32-characters-secret"
$env:APP_ADMIN_USERNAME="admin"
$env:APP_ADMIN_PASSWORD="admin123"
```

Bash:

```bash
export PG_PASSWORD="your_postgres_password"
export SILICONFLOW_API_KEY="your_siliconflow_key"
export RABBITMQ_PASSWORD="placeholder"
export SECURITY_JWT_SECRET="replace-with-at-least-32-characters-secret"
export APP_ADMIN_USERNAME="admin"
export APP_ADMIN_PASSWORD="admin123"
```

除了上面的变量，Spring Boot 其它配置也都可以通过环境变量覆盖，例如：

- `SPRING_AI_OPENAI_BASE_URL`
- `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`
- `SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL`
- `APP_HYBRID_TOP_K`
- `APP_DOCUMENT_CHUNK_SIZE`

## 启动项目

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
./mvnw spring-boot:run
```

打包运行：

```bash
./mvnw clean package -DskipTests
java -jar target/springai-knowledge-server-0.0.1.jar
```

默认地址：

- 服务基地址：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

## 启动时自动初始化

应用首次启动时会自动完成以下内容：

- 创建角色 `ADMIN`、`USER`
- 为默认角色补齐权限集合
- 创建管理员账号 `admin / admin123`
- 初始化系统配置项到数据库
- 尝试更新 `app_role_permission` 的权限约束

默认权限：

- `ADMIN`：`USER_READ`、`USER_WRITE`、`ROLE_READ`、`ROLE_WRITE`、`DOC_READ`、`DOC_WRITE`、`CONFIG_READ`、`CONFIG_WRITE`、`LOG_READ`、`LOG_WRITE`、`FEEDBACK_READ`、`FEEDBACK_WRITE`
- `USER`：`DOC_READ`、`FEEDBACK_WRITE`

## 鉴权说明

公开接口只有：

- `POST /api/auth/login`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`
- `GET /actuator/health`

其余接口都需要 JWT。

登录示例：

```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

登录返回字段：

- `token`
- `tokenType`
- `expiresIn`
- `user`

后续请求需要携带：

```text
Authorization: Bearer <your_jwt_token>
```

退出登录：

```bash
curl -X POST "http://localhost:8080/api/auth/logout" \
  -H "Authorization: Bearer <your_jwt_token>"
```

## API 概览

### 认证

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 公开 |
| `POST` | `/api/auth/logout` | 已登录 |

### 用户与角色

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET` | `/api/users/me` | 已登录 |
| `PATCH` | `/api/users/me` | 已登录 |
| `GET` | `/api/users` | `USER_READ` |
| `POST` | `/api/users` | `USER_WRITE` |
| `PATCH` | `/api/users/{id}` | `USER_WRITE` |
| `GET` | `/api/roles` | `ROLE_READ` |
| `GET` | `/api/roles/{id}` | `ROLE_READ` |
| `POST` | `/api/roles` | `ROLE_WRITE` |
| `PATCH` | `/api/roles/{id}` | `ROLE_WRITE` |

### 文档

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET` | `/api/documents` | `DOC_READ` |
| `GET` | `/api/documents/{id}` | `DOC_READ` |
| `GET` | `/api/documents/chunks/{chunkId}` | `DOC_READ` |
| `POST` | `/api/documents/search` | `DOC_READ` |
| `POST` | `/api/documents/upload` | `DOC_WRITE` |
| `POST` | `/api/documents/{id}/file` | `DOC_WRITE` |
| `POST` | `/api/documents` | `DOC_WRITE` |
| `PATCH` | `/api/documents/{id}` | `DOC_WRITE` |
| `DELETE` | `/api/documents/{id}` | `DOC_WRITE` |
| `POST` | `/api/documents/{id}/reindex` | `DOC_WRITE` |
| `POST` | `/api/documents/reindex` | `DOC_WRITE` |

### 问答、反馈与日志

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `POST` | `/api/qa` | `DOC_READ` |
| `POST` | `/api/feedback` | `FEEDBACK_WRITE` |
| `GET` | `/api/feedback` | `FEEDBACK_READ` |
| `GET` | `/api/logs/qa` | `LOG_READ` |
| `GET` | `/api/logs/operations` | `LOG_READ` |

### 配置与系统

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET` | `/api/config` | `CONFIG_READ` |
| `GET` | `/api/config/{key}` | `CONFIG_READ` |
| `PUT` | `/api/config/{key}` | `CONFIG_WRITE` |
| `POST` | `/api/config/refresh` | `CONFIG_WRITE` |
| `GET` | `/api/system/boundary` | 已登录 |
| `GET` | `/api/system/status` | 已登录 |

完整请求体和响应结构请以 Swagger 为准。

## 典型调用示例

新建文本文档：

```bash
curl -X POST "http://localhost:8080/api/documents" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"员工手册",
    "content":"这里是文档正文",
    "allowedRoles":["ADMIN","USER"]
  }'
```

上传文件文档：

```bash
curl -X POST "http://localhost:8080/api/documents/upload" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -F "file=@./demo.pdf" \
  -F "title=制度手册" \
  -F "allowedRoles=ADMIN,USER"
```

替换已有文档文件：

```bash
curl -X POST "http://localhost:8080/api/documents/1/file" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -F "file=@./new-demo.txt" \
  -F "title=更新后的制度手册"
```

搜索可见文档：

```bash
curl -X POST "http://localhost:8080/api/documents/search" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"query":"报销制度"}'
```

发起问答：

```bash
curl -X POST "http://localhost:8080/api/qa" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"question":"报销审批流程是什么？"}'
```

`/api/qa` 返回结构包含：

- `answer`
- `documents`
- `sources`
- `qaLogId`

提交反馈：

```bash
curl -X POST "http://localhost:8080/api/feedback" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -H "Content-Type: application/json" \
  -d '{"qaLogId":1,"helpful":true,"comment":"回答准确"}'
```

查看系统状态：

```bash
curl "http://localhost:8080/api/system/status" \
  -H "Authorization: Bearer <your_jwt_token>"
```

刷新系统配置缓存：

```bash
curl -X POST "http://localhost:8080/api/config/refresh" \
  -H "Authorization: Bearer <your_jwt_token>"
```

## 文档入库规则

- 上传仅支持 `PDF` 与 `TXT`
- 单文件默认最大 50MB
- 上传文件会保存到 `app.document.storage-path` 指定目录，默认 `./data/documents`
- 新建文本文档与上传文件时都必须指定 `allowedRoles`
- `POST /api/documents/{id}/file` 中 `allowedRoles` 可省略，省略时沿用原权限
- PDF 会按页提取文本，片段保留 `pageNumber`、`startOffset`、`endOffset`
- 文本切块实际大小为 `min(chunk.size, chunk.embeddingSafeSize)`
- 更新文档正文会重建切片与向量；仅修改标题或角色时会刷新向量元数据

## 日志与分页查询

`/api/feedback`、`/api/logs/qa`、`/api/logs/operations` 都支持以下查询参数：

- `userId`
- `from`
- `to`
- `page`，默认 `0`
- `size`，默认 `50`，最大 `200`

时间参数支持：

- ISO-8601：`2026-03-12T00:00:00Z`
- 本地时间：`2026-03-12T00:00:00`
- 日期：`2026-03-12`

分页响应结构：

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "total": 0
}
```

## 动态配置键

系统启动时会初始化以下数据库配置：

- `chunk.size`
- `chunk.overlap`
- `chunk.embeddingSafeSize`
- `hybrid.topK`
- `hybrid.vectorTopK`
- `hybrid.vectorSimilarityThreshold`
- `hybrid.keywordTopK`
- `hybrid.vectorWeight`
- `hybrid.keywordWeight`
- `keyword.tsConfig`
- `rag.answerStyle`
- `rag.maxAnswerChars`
- `rag.maxOutputTokens`
- `rag.temperature`
- `rag.topP`
- `rag.prompt.system`
- `rag.prompt.user`
- `system.boundary`

系统配置采用内存缓存，默认 TTL 为 `2000ms`。调用 `POST /api/config/refresh` 后会立即失效重载。

## 常见问题

- 启动报错 `Could not resolve placeholder 'PG_PASSWORD'`：未设置数据库密码环境变量
- 启动报错 `Could not resolve placeholder 'SILICONFLOW_API_KEY'`：未设置模型服务 API Key
- 启动报错 `Could not resolve placeholder 'RABBITMQ_PASSWORD'`：虽然当前没有 MQ 业务逻辑，但配置里仍引用了该占位符
- 启动报错 `JWT secret must be at least 32 characters`：请设置长度不少于 32 的 `SECURITY_JWT_SECRET`
- 报错 `type "vector" does not exist`：数据库未启用 `pgvector`
- 上传失败：仅支持 `PDF/TXT` 且默认大小不超过 50MB
- `/api/system/status` 或 `/api/system/boundary` 返回 401：这两个接口不是公开接口，需要先登录
