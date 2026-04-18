# SpringAI Knowledge Server (企业级知识库后端)

> 基于 **Spring Boot 4.0.5**、**Spring AI 2.0.0-M4** 与 **PostgreSQL (pgvector)** 打造的高性能、企业级 RAG (Retrieval-Augmented Generation) 知识库后端服务。
> 提供极致压缩与聚合的数据层支撑，为前端的高密度“一屏流”控制台提供坚实的数据基座，完美支持多模态混合检索、语义重排（Rerank）以及精密无缝的 RBAC 权限体系。

---

## 🚀 核心特性

- 🛡️ **内生级安全与审计**: 
  - 基于 **Spring Security** & **JWT** 的无状态鉴权防线。
  - **动态验证码防护**: 全新集成 `easy-captcha`，支持滑动与点选等多种验证码策略，强力抵御爆破攻击。
  - 精密的 **RBAC**（用户-角色-权限）隔离模型，支持细粒度至文档级别的权限访问管控。
  - **全方位审计**: 提供操作日志、AI 问答日志、用户反馈日志的深度追踪，并支持 **Excel 一键导出**（基于 Apache POI）。
- 📄 **多维格式文档中台**: 
  - 极度包容的文件支持引擎：可流畅解析 `PDF`, `DOCX`, `PPTX`, `XLSX`, `TXT`, `MD`, `HTML`, `CSV`。
  - **智能异步解析**: 引入 `DocumentProcessorHelper` 优化解析链路，利用 **RabbitMQ** 削峰填谷。
  - **AI 知识沉淀**: 解析过程中自动提取文档摘要并生成 **AI 推荐问题**，助力用户快速上手。
- 🔍 **复合型智能检索引擎**:
  - **混合搜索 (Hybrid Search)**: 向量检索（Semantic 语义感知）与全文检索（Keyword 精准命中）的深度加权融合。
  - **重排优化 (Rerank)**: 接入大厂级重排通道（**SiliconFlow**），对检出片段进行二次打分，显著提升回复准召率。
- 🤖 **大模型 RAG 生态闭环**:
  - 利用 **Spring AI** 彻底打通国内主流大模型生态，支持平滑的流式输出 (SSE) 交互。
  - **高性能模型组合**: 默认搭载强大的 **Qwen/Qwen3-14B** 对话模型与 **BAAI/bge-large-zh-v1.5** (1024维度) 向量模型，实现极速推理与精准匹配。
  - **精准溯源**: 提供绝对精准的文档级与段落级反向溯源定位（确保 RAG 体系的不“胡编乱造”）。
- 📊 **集约化数据观测基座**:
  - 高度抽象与聚合的 `Dashboard API`，配合本地 **Caffeine 高速缓存** 优化大屏接口响应性能。
  - 原生级集成 **Spring Boot Actuator** 构建系统状态监控探针。
- ⚙️ **无缝热更新运维**:
  - 提供运行时态的动态能力：检索融合权重、大模型生成参数、片段规模及安全阈值等，皆可通过面板一键热刷。

---

## 🛠 现代技术深度栈

- **核心驱动层**: Java 21 / Spring Boot 4.0.5
- **AI 智能编排**: Spring AI 2.0.0-M4
- **向量检索引擎**: PostgreSQL 15+ (深度搭载 `pgvector` 扩展，1024维向量表引擎)
- **高性能消息总线**: RabbitMQ
- **底层模型调用链**: SiliconFlow (模型组: Qwen3-14B, bge-large-zh-v1.5)
- **缓存与验证安全**: Caffeine Local Cache, easy-captcha 1.6.2
- **元数据与文档说明**: SpringDoc OpenAPI 3.0 (高可用 Swagger UI 集成)
- **硬核构建套件**: Maven, Docker, Lombok, Jackson, Apache POI, PDFBox, Jsoup

---

## 📦 开发部署指引

### 1. 必要环境清单
- **强类型平台**: JDK 21
- **持久化方案**: PostgreSQL 15+ (必需安装并强行挂载 `vector` 扩展环境)
- **消息驱动层**: RabbitMQ 3.x+
- **模型驱动秘钥**: SiliconFlow API Key

### 2. 构建向量存储空间
```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 环境挂载清单
核心变量支持通过系统环境或 `application.yml` 高级挂载配置注入：
- `PG_PASSWORD`: 连接数据库的凭证暗号
- `RABBITMQ_PASSWORD`: 中间件唤醒口令
- `SILICONFLOW_API_KEY`: 驱动大模型执行机的令牌
- `SECURITY_JWT_SECRET`: 签名下发的 JWT 核弹级密钥（入驻生产环境首要强制更换）

### 4. 熔铸与启动
```bash
# 铸造执行包（跨平台兼容打制）
./mvnw clean package -DskipTests

# 引擎点火运行
java -jar target/springai-knowledge-server-1.0.0.jar
```

---

## 📖 极速开发者接入

- **接口蓝图 (Swagger)**: `http://localhost:8080/swagger-ui/index.html`
- **生命探针**: `http://localhost:8080/actuator/health`
- **默认管理员**: `admin` / `admin123` (基于最新验证码模块保护入口)

---

## 📂 领域化代码模组设计

我们采用高内聚低耦合的限界上下文分层：
- `core`: 掌管系统心脏的生命周期（自动装配、顶层异常拦截与通用工具链）。
- `security`: 构建防线，涵跨 Token 颁发、动态图形验证码下发、路由级授权等鉴权中枢机制。
- `modules.knowledge`: 文档生命周期中枢。负责从上传落盘到 RabbitMQ 异步解析，由 `DocumentProcessorHelper` 协调分段与向量化雕刻。
- `modules.aiqa`: 面向用户的对话中继站（整合混合检索、Rerank 优化策略、AI 推荐问题与流式 SSE 下发）。
- `modules.system`: 支撑层底座。专司审计追踪（含 Excel 一键导出机制）、Caffeine 数据聚合缓存、运行时配置热更新与宏观 Dashboard 数据投喂。
