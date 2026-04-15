# SpringAI Knowledge Server (企业级知识库后端)

> 基于 **Spring Boot 4.x**、**Spring AI** 与 **PostgreSQL (pgvector)** 打造的高性能、企业级 RAG (Retrieval-Augmented Generation) 知识库后端服务。
> 提供极速的文档解析、多模态混合检索、语义重排（Rerank）以及完善的 RBAC 权限管理体系。

---

## 🚀 核心特性

- 🛡️ **安全与审计**: 
  - 基于 **Spring Security** & **JWT** 的无状态鉴权。
  - 细粒度的 **RBAC** 权限模型（用户-角色-权限）。
  - 全量的操作日志与 AI 问答日志追踪。
- 📄 **多格式文档中台**: 
  - 支持 `PDF`, `DOCX`, `PPTX`, `XLSX`, `TXT`, `MD`, `HTML`, `CSV`。
  - 异步文档解析引擎，通过 **RabbitMQ** 实现削峰填谷。
- 🔍 **智能检索引擎**:
  - **混合搜索 (Hybrid Search)**: 向量检索（Semantic）与全文检索（Keyword）的加权融合。
  - **重排优化 (Rerank)**: 接驳 **SiliconFlow** 进行文档片段二次打分，显著提升回答准确率。
- 🤖 **大模型 RAG 闭环**:
  - 深度集成 **Spring AI**，支持流式 (SSE) 与同步响应。
  - 精准的文档来源溯源（引用参考段落与页面偏移）。
- 📊 **系统监控**:
  - 集成 **Spring Boot Actuator**。
  - 提供实时系统状态、文档分布、Q&A 趋势等数据的聚合接口。
- ⚙️ **动态运维**:
  - 检索权重、大模型参数、向量切分阈值等核心配置支持在线热更新。

---

## 🛠 技术深度栈

- **核心引擎**: Java 21 / Spring Boot 4.0.3
- **AI 编排**: Spring AI 2.0.0-M2
- **向量数据库**: PostgreSQL 15+ (`pgvector` expansion)
- **消息队列**: RabbitMQ (用于文档异步解析)
- **大模型支持**: SiliconFlow (默认模型: Qwen3-14B, bge-large-zh, bge-reranker)
- **API 文档**: SpringDoc OpenAPI 3.0 (Swagger UI)
- **工具链**: Maven, Docker, Lombok, Jackson

---

## 📦 快速部署

### 1. 环境依赖
- **JDK 21**
- **PostgreSQL 15+** (需安装并启用 `vector` 扩展)
- **RabbitMQ 3.x+**
- **SiliconFlow API Key**

### 2. 数据库准备
```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置说明
主要环境变量（可在 `application.yml` 或系统环境变量中配置）：
- `PG_PASSWORD`: 数据库密码
- `RABBITMQ_PASSWORD`: RabbitMQ 密码
- `SILICONFLOW_API_KEY`: SiliconFlow 凭据
- `SECURITY_JWT_SECRET`: JWT 签名密钥（生产环境务必修改）

### 4. 编译与运行
```bash
# 构建项目
./mvnw clean package -DskipTests

# 运行服务
java -jar target/springai-knowledge-server-0.0.1.jar
```

---

## 📖 开发者向导

- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **健康检查**: `http://localhost:8080/actuator/health`
- **默认管理员**: `admin` / `admin123`

---

## 📂 项目模块结构

- `core`: 核心配置、异常处理、通用工具及初始化引导。
- `security`: 安全认证与 JWT 核心逻辑。
- `modules.knowledge`: 文档管理、解析引擎、向量化调度。
- `modules.aiqa`: 智能问答、混合检索、Rerank、反馈机制。
- `modules.system`: 用户/角色管理、系统配置、监控看板数据、日志审计。
