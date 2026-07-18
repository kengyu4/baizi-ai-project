# 白子 AI 学习与模拟面试平台

白子 AI 是一个面向学习与求职准备的全栈应用。它提供课程学习、AI 辅导、可见化学习记录，以及由岚川主持的简历驱动模拟面试。

## 核心能力

- **AI 学习助手**：课程学习、知识点讲解、自由提问和学习记录。
- **岚川模拟面试官**：上传或粘贴简历后，先完成自我介绍；系统据此、目标岗位和项目/实习经历自由提问。
- **深度追问与复盘**：每道正式题可围绕个人贡献、技术取舍、问题处理和结果最多追问两层，再给出单题评分和整体复盘。
- **简历 OCR**：支持文本简历，以及通过百度 OCR 识别图片和扫描版 PDF；识别结果可由用户核对后保存。
- **可配置 AI 模型**：后端支持兼容 OpenAI 协议的模型服务与多种提供商配置。

## 技术架构

```text
frontend/                         Vue 3 + TypeScript + Vite
xiaorong-teacher-assistant/       Java 17 + Spring Boot
  ├─ MySQL                        业务数据与面试记录
  ├─ Redis                        缓存
  ├─ RabbitMQ                     异步任务
  └─ 百度 OCR / 大模型 API         外部智能服务
```

## 项目目录

```text
.
├─ frontend/                       Vue 前端工程
├─ xiaorong-teacher-assistant/     Spring Boot 后端与 Docker Compose 配置
├─ dev-docs/                       项目设计、接口与部署文档索引
└─ interactive-quiz-demo/          课程与模板资源
```

## 快速启动（Docker Compose）

### 前置条件

- Docker Desktop / Docker Engine，并包含 Docker Compose
- 如需调用真实 OCR 或大模型，还需要准备对应服务的 API Key

### 1. 配置环境变量

```powershell
cd xiaorong-teacher-assistant
Copy-Item .env.example .env
```

编辑 `.env`，至少将数据库和 RabbitMQ 的占位密码改为安全值；如需启用百度 OCR，再填写 `BAIDU_OCR_API_KEY`、`BAIDU_OCR_SECRET_KEY` 并将 `XIAORONG_INTERVIEW_OCR_ENABLED` 设为 `true`。

### 2. 构建并启动

```bash
docker compose up -d --build
```

启动后访问：

- 前端：`http://localhost:8088`
- 后端调试接口：`http://localhost:18088`
- RabbitMQ 管理界面：`http://localhost:15672`
- MySQL：`localhost:13306`

> 后端镜像使用多阶段构建，会在 Docker 内执行 Maven 打包；不再依赖本机预先生成的 `target/*.jar`。

## 本地开发

### 前端

```bash
cd frontend
npm ci
npm run dev
```

质量检查：

```bash
npm test
npm run type-check
npm run build-only
```

### 后端

```bash
cd xiaorong-teacher-assistant
mvn test
mvn -DskipTests package
```

## 配置与安全

- `.env`、`application-local.yml` 和任何 API Key 均已被 Git 忽略，**不要提交密钥**。
- 可提交的配置样例请写入 `.env.example`，不要写入真实凭证。
- 在生产环境中请替换 Compose 中的默认密码，并定期备份 MySQL 数据卷。
- OCR 识别结果可能存在版式或换行误差，保存简历前应允许用户核对与修正。

## 文档

开发设计、接口说明和部署说明见 [dev-docs/README.md](./dev-docs/README.md)。