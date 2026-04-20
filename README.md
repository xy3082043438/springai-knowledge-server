# SpringAI Knowledge Base - Server (后端服务)

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-green.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)

本仓库是 **SpringAI Knowledge** 项目的后端服务端，基于 Spring Boot 4.x 和 Spring AI 体系构建，旨在提供企业级的私有化知识库管理与 RAG（检索增强生成）智能问答能力。

> **💡 完整项目提示**
> 本项目采用前后端分离架构，当前为**后端服务**仓库。
> 配套的**前端控制台**（Vue 3 + Vite）请访问：[springai-knowledge-web](前端仓库链接请替换至此处)

## 🏗️ 系统整体架构图

*(建议将根目录的 `系统总体架构.png` 以及 `混合检索与问答流程.png` 图传到仓库中，并在此处展示，让用户一目了然看清全局)*
![系统总体架构](./系统总体架构.png)

## ✨ 核心特性

- **📄 多格式文档解析与切分**：集成 Apache PDFBox, POI, Jsoup 等库，支持主流格式（PDF/Word/TXT/Markdown/HTML 等）文档解析，并通过 Spring AI 提供多策略的文本分段（Chunking）。
- **🔍 混合检索与 RAG 引擎**：深度集成 PostgreSQL 的 `pgvector` 扩展，支持文本向量化检索及结合大语言模型（LLM）的智能流式推理问答。
- **⚡ 异步架构**：基于 RabbitMQ 消息队列的异步非阻塞文档处理机制，确保大文件向量化时不阻塞主线程，提升系统吞吐量。
- **🛡️ RBAC 安全认证**：整合 Spring Security 结合 JWT 和 Easy Captcha 提供完善的权限访问控制（用户-角色-权限），支持文档的细粒度可见性。
- **📊 监控与统计分析**：提供对问答记录、请求量、系统资源等详尽的端点数据监控支持。

## 🛠️ 技术栈核心

- **核心框架**: Java 21, Spring Boot 4.0.5
- **持久层框架**: Spring Data JPA
- **AI 框架**: Spring AI (2.0.0-M4), 兼容 OpenAI API
- **向量数据库**: PostgreSQL + pgvector
- **消息队列**: RabbitMQ
- **安全与认证**: Spring Security, JWT (jjwt), 图形/滑块验证码
- **快速开发工具**: Lombok, MapStruct
- **API 文档**: Springdoc OpenAPI / Swagger UI

## 📂 项目模块结构

项目按高内聚低耦合原则划分结构：

```text
springai-knowledge-server/
├── src/main/java/com/lamb/springaiknowledgeserver
│   ├── config/         # 核心配置 (Security, Redis, Swagger, RabbitMQ 等)
│   ├── controller/     # 面向前端的 API 服务入口
│   ├── entity/         # 数据库映射实体类 (JPA Entities)
│   ├── repository/     # 数据访问层 (JPA Repositories)
│   ├── service/        # 核心业务逻辑层 (涵盖 RAG、文件处理、认证、系统功能)
│   ├── security/       # JWT 认证拦截、UserDetailsService、RBAC鉴权逻辑
│   ├── exception/      # 全局统一异常处理与自定义异常
│   └── utils/          # 工具包 (响应封装、加密等全局工具类)
└── src/main/resources
    ├── application.yml # 环境与基础系统配置
    └── captcha/        # 图形/拼图验证码底层资源文件
```

## 🚀 快速启动

### 1. 环境依赖与准备

- **JDK**: 21 
- **数据库**: PostgreSQL 15+ (必须安装 `vector` 扩展)
- **中间件**: RabbitMQ 3.x
- **大模型 API Key**: 兼容 OpenAI 的 API Key (例如：ChatGPT / 硅基流动 SiliconFlow / 阿里云百炼等)

### 2. 数据库初始化

连接至 PostgreSQL 并初始化数据库及扩展：
```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```
*提示：Spring Data JPA 将会自动在服务启动时通过 `hibernate.ddl-auto` 构建应用基础表结构。*

### 3. 系统核心配置

在 `src/main/resources/application.yml` 中配置相应的外部参数：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/springai_knowledge
    username: <your-db-username>
    password: <your-db-password>
  rabbitmq:
    host: localhost
    port: 5672
    username: <rabbit-user>
    password: <rabbit-pass>
  ai:
    openai:
      api-key: <your-api-key>
      base-url: <your-base-url>
```

### 4. 编译与启动服务

利用内置的 Wrapper 快速运行项目：
```bash
# Windows
.\mvnw.cmd clean install -DskipTests
.\mvnw.cmd spring-boot:run

# Linux / MacOS
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```
服务默认监听在 `8080` 端口。

## 📖 API 接口文档

项目启动后，可通过 Swagger UI 访问完整的 API 接口文档并在线调试：
- **Swagger UI** 地址：[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Actuator 监控端点**：[http://localhost:8080/actuator](http://localhost:8080/actuator)

## 🤝 贡献与规范

- 代码风格保持统一，提倡使用完整的 RESTful API 命名规范。
- 新增业务务必编写单元测试（使用 JUnit 5 + Mockito）并在 Controller 层加上详细的 `@Operation` 注解。
# 🧠 SpringAI Knowledge Server

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M4-green.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg)](https://www.postgresql.org/)

本仓库是 **SpringAI Knowledge** 项目的后端服务提供完整的企业级知识库、RAG (检索增强生成) 问答、与混合检索能力。

## ✨ 核心特性

- **📄 多格式文档解析**：支持 PDF、Word、PPT、Excel、TXT、Markdown、HTML、CSV 等格式的解析与入库。
- **🔍 混合检索与 RAG**：基于 PostgreSQL `pgvector` 提供向量检索与关键字混合检索，结合大模型提供精准的流式问答（SSE）。
- **🛡️ 完善的安全控制**：使用 Spring Security + JWT 实现无状态认证，提供基于 RBAC 的精细化权限与文档可见性控制。
- **⚙️ 动态系统配置**：支持动态调整 Chunk 策略、混合检索权重、大模型生成参数 (Temperature, TopP等) 及 Prompt 模板。
- **📊 审计与监管**：完整的操作日志、问答日志追踪，支持用户对问答结果进行反馈与修正。

## 🛠️ 技术栈

- **核心框架**: Java 21, Spring Boot 4.0.5, Spring MVC
- **AI 与向量库**: Spring AI 2.0.0-M4, PostgreSQL + `pgvector` 扩展
- **消息队列**: RabbitMQ (用于文档异步解析与向量化)
- **大模型接口**: 兼容 OpenAI 格式的 API (默认配置为 SiliconFlow)
- **文档处理**: Apache PDFBox, Apache POI, Jsoup
- **API 文档**: springdoc-openapi (Swagger UI)

## 🚀 快速开始

### 1. 环境准备

- **JDK 21**
- **Maven 3.9+** (也可使用自带的 `mvnw`)
- **PostgreSQL 15+** (必须安装并启用 `vector` 扩展)
- **RabbitMQ 3.x**

### 2. 数据库初始化

```sql
CREATE DATABASE springai_knowledge;
\c springai_knowledge;
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置环境变量

项目依赖以下关键环境变量（可在 IDE 或启动脚本中配置）：

```bash
export PG_PASSWORD=你的PG数据库密码
export RABBITMQ_PASSWORD=你的RabbitMQ密码
export SILICONFLOW_API_KEY=你的大模型API_KEY
```
*(注：生产环境建议进一步修改为实际的 `security.jwt.secret`、`app.admin.password` 等参数)*

### 4. 启动服务

**以 Maven 运行 (Windows):**
```powershell
.\mvnw.cmd clean package -DskipTests
.\mvnw.cmd spring-boot:run
```

**以 Maven 运行 (Linux/macOS):**
```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

服务默认运行在 `8080` 端口。

## 📖 接口文档与默认账号

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **健康检查**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

**默认管理员账号** (应用启动后自动初始化)：
- 用户名：`admin`
- 密码：`admin123`

*(⚠️ 强烈建议在生产环境通过配置文件修改默认的 `app.admin.username` 和 `password`)*

## 📂 项目结构简介

```text
src/main/java/com/lamb/springaiknowledgeserver
├── core        # 基础核心层 (异常处理、公共DTO、全局配置、启动初始化)
├── security    # 安全层 (JWT认证、验证码、登录鉴权)
└── modules     # 业务模块层
    ├── aiqa      # 智能问答、会话管理、问答反馈与日志
    ├── knowledge # 知识库、文档解析、分段(Chunk)与向量检索
    └── system    # 系统管理 (用户、角色、配置、系统日志、统计仪表盘)
```
