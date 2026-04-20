# SpringAI Knowledge Server

基于 Spring Boot 4、Spring AI、PostgreSQL `pgvector` 和 RabbitMQ 的知识库后端服务，提供文档入库、分段向量化、混合检索、RAG 问答、权限控制、审计日志和系统配置管理能力。

## 项目定位

这个项目是一个面向企业知识库场景的后端服务，核心职责包括：

- 文档管理：上传、替换、检索、重建索引、文件预览
- 知识问答：基于文档分段和向量检索生成回答，支持流式输出
- 权限控制：基于 JWT + Spring Security + RBAC 控制访问范围
- 系统运营：用户、角色、日志、反馈、仪表盘、运行时配置

## 技术栈

- Java 21
- Spring Boot 4.0.5
- Spring AI 2.0.0-M4
- Spring MVC / Spring Security / Spring Data JPA / Actuator
- PostgreSQL + `pgvector`
- RabbitMQ
- OpenAI 兼容模型接口（当前配置为 SiliconFlow）
- Apache PDFBox / Apache POI / Jsoup
- springdoc-openapi

## 核心能力

### 1. 文档知识入库

支持以下文件格式：

- `PDF`
- `DOCX`
- `PPTX`
- `XLSX`
- `TXT`
- `MD`
- `HTML`
- `CSV`

文档处理流程：

1. 上传文件或直接创建文本型文档
2. 提取正文内容
3. 按配置切分为多个 chunk
4. 写入业务表并同步到 `pgvector`
5. 为问答、搜索和溯源提供基础数据

项目中还提供：

- 单文档重建索引
- 全量重建索引
- chunk 预览
- 原始文件在线预览
- 基于角色的文档可见性控制

### 2. RAG 问答

问答模块提供：

- 普通问答接口
- SSE 流式问答接口
- 会话历史查询
- 推荐问题生成与补全
- 问答日志与用户反馈

当前默认模型相关配置来自 `application.yml`：

- Chat：`Qwen/Qwen3-14B`
- Embedding：`BAAI/bge-large-zh-v1.5`
- Rerank：`BAAI/bge-reranker-v2-m3`

### 3. 混合检索与动态参数

项目同时维护了应用配置和系统配置表：

- 静态配置：数据库、RabbitMQ、模型网关、上传目录等
- 动态配置：chunk 参数、混合检索参数、生成参数、Prompt 模板等

启动时会自动初始化一批系统配置项，例如：

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

### 4. 安全与审计

安全模型由以下几部分组成：

- `JWT` 无状态认证
- `Spring Security` 请求保护
- 方法级权限控制 `@PreAuthorize`
- 基于角色的文档访问控制
- 操作日志、问答日志、反馈日志
- 日志导出为 Excel

默认匿名开放的接口：

- `POST /api/auth/login`
- `GET /api/auth/captcha`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `GET /actuator/health`

其余接口默认都需要认证。

## 默认初始化数据

应用启动后会自动初始化：

- `ADMIN` 角色：拥有全部权限
- `USER` 角色：默认拥有 `DOC_READ`、`FEEDBACK_WRITE`、`QA_READ`、`DASHBOARD_READ`
- 管理员账号：来自 `app.admin.username` / `app.admin.password`

当前仓库默认配置为：

- 用户名：`admin`
- 密码：`admin123`

建议在真实环境中覆盖该配置，不要直接使用默认值。

## 环境要求

- JDK 21
- Maven 3.9+（或直接使用仓库自带 `mvnw` / `mvnw.cmd`）
- PostgreSQL 15+，并安装 `vector` 扩展
- RabbitMQ 3.x
- 可访问的 OpenAI 兼容模型网关

## 本地开发配置

### 1. 初始化 PostgreSQL

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 配置环境变量

项目当前通过环境变量读取以下敏感信息：

```bash
PG_PASSWORD=your-postgres-password
RABBITMQ_PASSWORD=your-rabbitmq-password
SILICONFLOW_API_KEY=your-api-key
```

如果你要用于生产环境，至少还需要修改：

- `security.jwt.secret`
- `app.admin.username`
- `app.admin.password`
- `spring.datasource.url`
- `spring.rabbitmq.host`

### 3. 关键配置项

`src/main/resources/application.yml` 中当前已包含以下配置类别：

- 数据库连接
- RabbitMQ 连接
- Spring AI 模型与向量库配置
- 文件上传大小限制
- JWT 配置
- 文档存储路径与分块参数
- RAG 输出参数
- 混合检索参数
- Rerank 参数

默认本地目录：

- 上传目录：`./data/uploads`
- 文档存储目录：`./data/documents`

## 启动方式

### Maven

Windows：

```powershell
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

### Jar 包运行

```bash
java -jar target/springai-knowledge-server-1.0.0.jar
```

### Docker

仓库包含 `Dockerfile`，可自行构建镜像：

```bash
docker build -t springai-knowledge-server .
docker run --rm -p 8080:8080 springai-knowledge-server
```

实际运行时仍需自行注入数据库、RabbitMQ 和模型服务相关配置。

## 接口入口

启动后可访问：

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

## 主要接口分组

### 认证

- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/logout`

### 文档管理

- `GET /api/documents`
- `GET /api/documents/{id}`
- `GET /api/documents/chunks/{chunkId}`
- `POST /api/documents/search`
- `POST /api/documents/upload`
- `POST /api/documents/{id}/file`
- `POST /api/documents`
- `PATCH /api/documents/{id}`
- `DELETE /api/documents/{id}`
- `POST /api/documents/{id}/reindex`
- `POST /api/documents/reindex`
- `GET /api/documents/{id}/file`

### 问答与会话

- `POST /api/qa`
- `POST /api/qa/stream`
- `GET /api/qa/suggestions`
- `POST /api/qa/suggestions/backfill`
- `GET /api/aiqa/sessions`
- `GET /api/aiqa/sessions/{id}/logs`
- `DELETE /api/aiqa/sessions/{id}`

### 反馈与日志

- `POST /api/feedback`
- `GET /api/feedback`
- `GET /api/feedback/export`
- `GET /api/logs/qa`
- `GET /api/logs/qa/export`
- `GET /api/logs/operations`
- `GET /api/logs/operations/export`

### 系统管理

- `GET /api/dashboard`
- `GET /api/system/status`
- `GET /api/config`
- `GET /api/config/{key}`
- `PUT /api/config/{key}`
- `POST /api/config/refresh`
- `GET /api/users/me`
- `PATCH /api/users/me`
- `PATCH /api/users/me/password`
- `GET /api/users`
- `GET /api/users/{id}`
- `POST /api/users`
- `PATCH /api/users/{id}`
- `DELETE /api/users/{id}`
- `GET /api/roles`
- `GET /api/roles/{id}`
- `GET /api/roles/permissions`
- `POST /api/roles`
- `PATCH /api/roles/{id}`
- `DELETE /api/roles/{id}`

## 项目结构

```text
src/main/java/com/lamb/springaiknowledgeserver
├─ core
│  ├─ bootstrap    # 启动初始化：管理员、角色、系统配置、状态恢复
│  ├─ config       # 安全、OpenAPI、AI 等基础配置
│  ├─ dto          # 通用 DTO
│  ├─ exception    # 全局异常处理
│  └─ util         # 通用工具
├─ security
│  └─ auth         # JWT、登录、验证码、用户鉴权
└─ modules
   ├─ aiqa         # 问答、会话、反馈、问答日志
   ├─ knowledge    # 文档、chunk、索引与检索
   └─ system       # 用户、角色、配置、日志、仪表盘、上传
```

## 开发建议

- 当前 `application.yml` 中包含固定地址和默认账号，更适合作为开发环境模板
- 生产环境建议拆分 `application-dev.yml` / `application-prod.yml`
- `spring.jpa.hibernate.ddl-auto` 当前为 `update`，上线前建议改为更可控的迁移方案
- 建议配合 Flyway 或 Liquibase 管理数据库 schema

## 许可证

仓库当前未声明许可证；如需开源发布，建议补充明确的 License 文件。
