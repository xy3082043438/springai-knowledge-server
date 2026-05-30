# SpringAI Knowledge Base · Server 后端服务

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-green.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)

**SpringAI Knowledge** 项目的后端服务，基于 Spring Boot 4 + Spring AI 构建，提供知识库管理、文档异步向量化、混合检索 + 重排，以及大模型 RAG（检索增强生成）问答接口。

> 本项目为前后端分离架构，此处为**后端仓库**。配套的前端控制台（Vue 3 + Vite）见 [springai-knowledge-web](https://github.com/xy3082043438/springai-knowledge-web)。

## ✨ 核心特性

- **📄 多格式文档解析与切分**：集成 Apache PDFBox / POI / Jsoup，支持 PDF、Word、PPTX、XLSX、TXT、Markdown、HTML、CSV 等格式的解析、分块（Chunking）与入库，并在解析过程中自动生成文档摘要与 AI 推荐问题。
- **🔍 混合检索 + RRF 融合 + 重排 RAG**：基于 PostgreSQL `pgvector` 的向量检索叠加全文检索，通过 RRF（Reciprocal Rank Fusion）按排名融合两路结果，再用 Rerank 模型二次重排，最终由大模型生成带来源引用的 SSE 流式回答。
- **⚡ 异步文档处理**：通过 RabbitMQ 将解析、切分与向量化放入异步队列，避免大文件阻塞主请求线程。
- **🛡️ RBAC 鉴权**：Spring Security + JWT 无状态认证，配合 Easy Captcha（算术图形验证码）与登录失败锁定，按用户—角色—权限三级模型控制访问，并支持配置文档可见性。
- **⚙️ 运行时配置**：支持在线调整分块策略、混合检索权重、大模型生成参数（Temperature / TopP 等）与 Prompt 模板。
- **📊 日志与监控**：记录操作日志与问答日志，支持用户对答案反馈；通过 Spring Boot Actuator 暴露健康检查与监控端点。

## 🛠️ 技术栈

- **核心框架**：Java 21、Spring Boot 4.0.5、Spring MVC
- **AI 框架**：Spring AI 2.0.0-M4（`spring-ai-starter-model-openai`，兼容 OpenAI 接口）
- **大模型服务**：SiliconFlow（默认）
  - 对话模型：`Qwen/Qwen3-14B`
  - 向量模型：`BAAI/bge-large-zh-v1.5`（1024 维）
  - 重排模型：`BAAI/bge-reranker-v2-m3`
- **向量存储**：PostgreSQL + `pgvector`（表 `document_embedding`）
- **持久层**：Spring Data JPA
- **消息队列**：RabbitMQ（Spring AMQP）
- **安全认证**：Spring Security、JWT（jjwt）、Easy Captcha
- **文档处理**：Apache PDFBox、Apache POI、Jsoup
- **其他**：Caffeine Cache、Spring Boot Actuator、Springdoc OpenAPI (Swagger UI)

## 📂 项目结构

```text
src/main/
├── java/com/lamb/springaiknowledgeserver/
│   ├── core/            # 基础核心层：全局配置、公共 DTO、异常处理、工具、启动初始化
│   ├── security/        # 安全层：JWT 认证、验证码、登录鉴权 (auth)
│   └── modules/         # 业务模块层
│       ├── aiqa/        # 智能问答：对话 (chat)、问答反馈 (feedback)
│       ├── knowledge/   # 知识库：文档 (document) 解析、分块、向量检索
│       └── system/      # 系统管理：config / dashboard / log / role / upload / user
└── resources/
    └── application.yml  # 环境与系统配置（通过环境变量注入敏感项）
```

## 🚀 快速开始

### 1. 环境准备

- **JDK 21**
- **Maven 3.9+**（也可直接使用自带的 `mvnw` / `mvnw.cmd`）
- **PostgreSQL 15+**（必须启用 `vector` 扩展）
- **RabbitMQ 3.x / 4.x**（部署镜像为 `rabbitmq:4.3`）
- **大模型 API Key**：兼容 OpenAI 的接口（默认 SiliconFlow，亦可用阿里云百炼等）

### 2. 数据库初始化

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

> 也可使用根目录 `docker-compose.yml`（内置 `dockerfile_inline`，基于 `pgvector/pgvector` 镜像并执行 [`docker/postgres-init.sql`](docker/postgres-init.sql)）一键构建带 pgvector 的数据库。应用启动时 Spring AI 会自动初始化向量表，JPA 会按 `ddl-auto` 构建业务表结构。

### 3. 配置环境变量

`application.yml` 通过环境变量注入敏感配置：

| 环境变量 | 说明 |
| --- | --- |
| `SERVER_IP` | 数据库 / 中间件主机地址（默认 `localhost`） |
| `DB_NAME` | 数据库名（默认 `springai_knowledge`） |
| `PG_PASSWORD` | PostgreSQL 密码 |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 |
| `SILICONFLOW_API_KEY` | 大模型服务 API Key |
| `JWT_SECRET` | JWT 签名密钥 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 初始管理员账号 / 密码 |

```bash
# Linux / macOS 示例
export PG_PASSWORD=your-pg-password
export RABBITMQ_PASSWORD=your-rabbitmq-password
export SILICONFLOW_API_KEY=your-api-key
export JWT_SECRET=your-jwt-secret
```

### 4. 启动服务

```bash
# Windows
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

服务默认运行在 `http://localhost:8080`。

## 📖 接口文档与默认账号

- **Swagger UI**：[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **健康检查**：[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

初始管理员账号由 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 指定（默认用户名 `admin`），应用首次启动时自动写入数据库。生产环境部署前请修改默认凭据与 `JWT_SECRET`。

## 🤝 贡献规范

- 遵循 RESTful API 命名规范，Controller 层方法补充 Springdoc 的 `@Operation` 注解。
- 新增业务建议配套单元测试（JUnit 5 + Mockito）。
