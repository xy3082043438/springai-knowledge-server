# SpringAI Knowledge Server (企业知识库后端)

> 基于 Spring Boot、Spring AI 和 PostgreSQL pgvector 向量数据库打造的企业级知识库后端服务。
> 支持多种文档格式解析、混合检索、可选重排（Rerank）、RAG 智能问答、以及完善的 RBAC 权限体系与系统监控。

## ✨ 核心特性

- 🔐 **安全与权限**: 基于 Spring Security 和 JWT 实现的无状态鉴权，提供细粒度的 RBAC（用户-角色-权限）控制，支持 Token 吊销机制。
- 📄 **多格式文档解析**: 支持 `PDF`, `DOCX`, `PPTX`, `XLSX`, `TXT`, `MD`, `HTML`, `CSV` 等多种格式上传。
- 🔍 **智能检索**: 
  - **向量检索 + 全文检索** 的混合加权融合机制。
  - 支持调用 **SiliconFlow** 进行文档片段的二次重排（Rerank），提升召回准确率。
- 🤖 **大模型 RAG 问答**: 对接触发大模型接口生成精准答案，提供引用参考文档段落与页面偏移，提高可解释性。
- 📊 **系统审计与监控**: 全面的操作日志、问答日志、反馈收集，搭配 Spring Boot Actuator 提供全方位监控。
- ⚙️ **动态配置热刷新**: 核心参数与 API Key 持久化在数据库中，支持控制台在线动态修改和热插拔。
- 📖 **API 开放文档**: 内置基于 `springdoc-openapi` 的 Swagger UI 页面。

## 🛠 技术栈

- **核心框架**: Java 21, Spring Boot 4.0.3
- **AI 编排**: Spring AI 2.0.0-M2
- **数据层**: Spring Data JPA, PostgreSQL 15+ (`pgvector` 扩展)
- **安全与工具**: Spring Security, JWT (`jjwt`), Lombok, PDFBox, Apache POI, jsoup, springdoc-openapi

## 🚀 快速启动

### 1. 环境准备

- [Java 21](https://jdk.java.net/21/)
- [PostgreSQL 15+](https://www.postgresql.org/) (必须安装并启用 `pgvector` 插件)
- 大模型 API 密钥 (默认使用 SiliconFlow，兼容 OpenAI API)

数据库初始化脚本：

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 核心配置与环境变量

项目启动前需要配置以下环境变量或在 `application.yml` 中修改：

| 变量名 | 必填 | 说明 |
| --- | --- | --- |
| `PG_PASSWORD` | 是 | PostgreSQL 数据库密码 |
| `SILICONFLOW_API_KEY` | 是 | Chat/Embedding/Rerank 等大模型调用的 API 凭据 |
| `SECURITY_JWT_SECRET` | 建议 | JWT 签名密钥 (至少32位)，生产环境务必替换 |
| `APP_ADMIN_USERNAME` | 否 | 系统初始管理员账号，默认: `admin` |
| `APP_ADMIN_PASSWORD` | 否 | 系统初始管理员密码，默认: `admin123` |
| `APP_DOCUMENT_STORAGE_PATH` | 否 | 上传文件本地落盘目录，默认: `./data/documents` |

**示例 (PowerShell / Bash):**
```bash
export PG_PASSWORD="your_db_password"
export SILICONFLOW_API_KEY="your_ai_api_key"
export SECURITY_JWT_SECRET="replace_with_your_super_secret_key"
```

### 3. 构建与运行

Windows:
```powershell
.\mvnw.cmd clean package -DskipTests
java -jar target/springai-knowledge-server-0.0.1.jar
```

Linux / macOS:
```bash
./mvnw clean package -DskipTests
java -jar target/springai-knowledge-server-0.0.1.jar
```

### 4. 服务入口

- **API 基地址**: `http://localhost:8080`
- **Swagger UI API 文档**: `http://localhost:8080/swagger-ui/index.html`
- **健康检查**: `http://localhost:8080/actuator/health`

首次启动后，系统将自动执行以下操作：
1. 自动执行表结构设计（JPA ddl-auto）。
2. 构建 `ADMIN` 与 `USER` 角色并授权。
3. 创建默认管理员用户 ( `admin` / `admin123` )。

---

## 📚 主要业务流程

1. **文档摄入**: 用户上传各类企业文档（如操作手册、规章制度）。系统利用 PDFBox/POI 等工具抽取文本，通过 Spring AI 进行 Chunk 切块。
2. **向量化录入**: 将 Chunk 基于 Embedding 模型向量化，并与 metadata 存入 `app_document_chunk` 及 `pgvector` 向量表。
3. **混合检索**: 用户发起提问时，同时执行向量检索与 PG 全文索引检索，两路召回结果按配置的权重合并。
4. **重排 (Rerank)**: 如开启重排，将混合检索结果交给大模型的 Rerank API 重新打分排序。
5. **LLM 响应产生**: 根据排序后的最佳片段作为 Context，组合 Prompt 后交由 Chat 模型生成答案，并流式或同步返回给前台。
