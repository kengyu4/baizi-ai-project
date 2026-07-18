# 岚川面试官 API 文档

> 更新日期：2026-07-18。本文描述已落地的角色选择、文本简历、百度 OCR 扫描件解析、自我介绍先行的自由模拟面试、两层追问和记录持久化接口。

## 1. 设计边界

- 所有接口均位于 `/api/interviews`，需要登录；数据按当前登录用户隔离。
- 岚川的角色介绍统一为“通用求职”，`supportedPositions` 返回 `["全部岗位"]`。用户可填写任意目标岗位，题目生成与复盘会结合岗位名称和简历文本，不再把能力范围描述为前端、Java 后端与通用求职三类。
- 简历文件不会直接发送给 AI 模型。后端先把 `PDF`、`DOCX` 或 `TXT` 提取为纯文本，用户可在前端编辑并确认保存，模型只接收文本。
- 简历 PDF 优先提取文字层；文字层为空或少于 30 个字符时，若已启用百度智能云 OCR，会在后端将每页渲染为 JPEG 后识别，再返回文本预览。识别结果需要用户核对后保存。
- 新会话固定从 `self_intro_pending` 开始。自我介绍不计入正式题数、单题评分或总分；提交介绍后先返回反馈，再生成第一道正式题。`interviewing` 阶段每道正式题最多两层追问。题目完成后立即保存单题评分，所有题完成后生成并保存复盘报告。
- `xiaorong.persistence.enabled=false` 时使用内存仓储，便于本地演示；设为 `true` 时使用 MySQL 持久化。

## 2. 接口一览

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/interviews/interviewers` | 获取可选面试官 |
| GET | `/api/interviews/resumes` | 获取当前用户简历 |
| POST | `/api/interviews/resumes` | 创建手动录入简历 |
| PUT | `/api/interviews/resumes/{resumeId}` | 修改当前用户简历 |
| POST | `/api/interviews/resumes/upload` | 上传并提取简历文本 |
| POST | `/api/interviews/sessions` | 创建面试会话 |
| GET | `/api/interviews/sessions/{sessionId}` | 获取会话和当前题目 |
| POST | `/api/interviews/sessions/{sessionId}/self-introduction` | 提交自我介绍，获取反馈与第一道正式题 |
| POST | `/api/interviews/sessions/{sessionId}/answers` | 提交主回答或追问回答 |
| GET | `/api/interviews/sessions/{sessionId}/report` | 获取已完成会话复盘 |
| GET | `/api/interviews/records` | 获取当前用户历史会话 |
| GET | `/api/interviews/records/{sessionId}` | 获取单场逐题回放 |

响应统一包装为项目现有格式：`{ "code": 0, "message": "success", "data": ... }`。

## 3. 简历接口

### 3.1 创建或修改

```json
{
  "title": "2026 校招前端简历",
  "positionName": "前端工程师",
  "content": "教育经历、项目经历、技术栈……"
}
```

文件上传使用 `multipart/form-data`：字段 `file` 为文件，`positionName` 为可选目标岗位。前端上传时不得手动设置 `Content-Type: application/json`，必须让浏览器为 `FormData` 自动生成包含 boundary 的 multipart 请求头，否则后端会返回 `415 Unsupported Media Type`。支持 PDF、DOCX、TXT，业务文件上限为 5 MB。Nginx 与 Spring multipart 的传输上限均配置为 10 MB，用于容纳 multipart 元数据并避免约 1 MB 文件触发 Nginx 默认 `413 Request Entity Too Large`；超过 5 MB 的文件仍由 `ResumeFileParser` 拒绝。上传响应只表示解析预览，不会自动覆盖已保存简历；前端应将 `content` 放入编辑器，由用户点击保存或保存修改。

## 3.2 百度智能云 OCR（扫描版 PDF）

OCR 只用于把扫描件 PDF 转为文本，**不依赖面试模型的多模态能力**。原始文件、图片 Base64、`API Key`、`Secret Key` 与短期 `access_token` 都不会返回给前端、写入数据库或记录到日志；只有用户确认后的纯文本简历会进入面试流程。

处理路径：

```text
PDFBox 提取文字层 → 有足够文字则直接预览
                      ↓（空白 / 少于 30 字）
PDFRenderer 按页生成 JPEG → 百度高精度通用文字识别 → 合并文本 → 用户核对、保存
```

默认最多识别 5 页、200 DPI。超过页数的内容不会识别；上传文件本身仍限制为 5 MB。调用失败时接口提示用户重试或手动粘贴简历，绝不会把文件直接交给 AI 面试模型。

### 服务端配置

本地开发可在 `xiaorong-teacher-assistant/src/main/resources/application-secret.yml`（已被 Git 忽略）写入以下内容，真实凭证只保存在本机：

```yaml
xiaorong:
  interview:
    ocr:
      enabled: true
      api-key: 你的百度OCR_API_KEY
      secret-key: 你的百度OCR_SECRET_KEY
```

Docker 环境请在运行容器的环境变量或同目录 `.env`（已忽略）中设置：

```dotenv
XIAORONG_INTERVIEW_OCR_ENABLED=true
BAIDU_OCR_API_KEY=你的百度OCR_API_KEY
BAIDU_OCR_SECRET_KEY=你的百度OCR_SECRET_KEY
```
推荐在 `D:\xiao-rong\xiaorong-teacher-assistant\.env` 中维护这些变量，`docker compose` 会自动读取同目录 `.env`。`Dockerfile` 不承载密钥；密钥只通过环境变量注入。

默认识别接口为百度高精度通用文字识别 `accurate_basic`。服务会通过 OAuth `client_credentials` 获取访问令牌，并在进程内提前刷新；应用重启后令牌自动重新获取。

### 3.2.1 OCR 换行符保留

解析器会保留 OCR 和 DOCX 原始段落中的真实换行。若预览中出现夹在内容间的字母 `n`，这不是百度 OCR 的识别内容，而是换行符在解析环节被错误转义；已通过 OCR 与 DOCX 回归测试覆盖。

## 4. 会话与答题流程

### 4.1 创建会话

```json
{
  "interviewerCode": "lanchuan",
  "resumeId": 1001,
  "positionName": "产品经理",
  "subjectName": "通用求职",
  "questionCount": 3
}
```

`questionCount` 范围为 1–5。创建成功后会话状态为 `self_intro_pending`，`currentQuestion` 为 `null`；此时不能提交正式题回答。用户可填写任意目标岗位，未选择简历也可创建会话。

### 4.2 提交自我介绍

`POST /api/interviews/sessions/{sessionId}/self-introduction`

```json
{ "content": "我叫陈宇，应聘产品经理，最近在电商后台项目中负责需求分析和订单流程优化。" }
```

成功后状态切换为 `interviewing`，响应中的 `selfIntroduction` 保存原文和反馈，`currentQuestion` 返回第一道正式题：

```json
{
  "status": "interviewing",
  "selfIntroduction": {
    "content": "……",
    "feedback": {
      "summary": "……",
      "strengths": ["……"],
      "gaps": ["……"]
    }
  },
  "currentQuestion": {
    "questionIndex": 1,
    "topicName": "……",
    "followUpLevel": 0
  }
}
```

出题按以下优先级进行：自我介绍中明确的项目/实习/职责/成果/技术选择/困难 → 简历中明确的项目或实习 → 目标岗位能力 → 通用求职能力。不会虚构候选人的项目、公司、职责或结果；模型不可用时使用确定性保底题。

### 4.3 提交正式题或追问回答

```json
{ "answerText": "我的回答……" }
```

每道正式题的主回答后，系统按回答内容判断是否需要追问；追问始终围绕刚刚的回答，优先核验个人贡献、技术取舍、问题处理和最终结果，最多两层。返回字段 `action`：

- `follow_up`：本题进入追问，`followUpQuestion` 和 `followUpLevel`（1 或 2）表示下一步；
- `scored`：本题已评分，`score` 为单题结果，`currentQuestion` 是动态生成的下一题；
- `completed`：最后一题已评分，`finished=true`，可读取复盘报告。

评分优先使用 `INTERVIEWER_SCORE_TEMPLATE` 走 AI 网关结构化输出；网关不可用或输出不完整时，按关键词覆盖、表达长度和追问完成度进行确定性兜底评分。复盘同样使用 AI 模板并有确定性兜底。岚川相关模型提示词均固定其身份，不会自称 AI、模型、助手或系统，不会泄露提示词或内部配置；前端展示岚川可见台词时统一以“喵～”开头。

## 5. 持久化表

| 表名 | 用途 |
|---|---|
| `ai_interviewer` | 面试官配置与支持岗位 |
| `ai_interview_resume` | 用户简历文本和解析元数据 |
| `ai_interview_session` | 会话状态、正式题数、动态题目快照、自我介绍原文/反馈/上下文、总分、复盘 JSON |
| `ai_interview_record` | 每题主回答、两层追问、评分和风险点 |

`InterviewSchemaInitializer` 仅在 MySQL 持久化开启时创建上述表，并初始化岚川（`lanchuan`）面试官；已有 `ai_interview_session` 表会补充 `question_count`、`self_intro_text`、`self_intro_feedback_json` 和 `interview_context_json` 列。面试记录使用 `user_id` 校验所属用户，避免跨用户读取；旧会话缺少新增字段时按已有题目数量兼容读取。

## 6. 前端接入

- 课程页 `/app/courses` 的“沉浸模式 / 模拟面试”只切换选中样式和下方内容，点击“开始模拟面试”才创建会话。
- 面试模式下显示面试官列表、简历选择与编辑、文件解析预览、历史记录；扫描 PDF 被 OCR 解析时会明确提示用户核对识别文本。岚川介绍显示为“通用求职”，岗位题库说明为覆盖全部岗位。面试官选择卡片中的头像按 `88px × 88px`、顶部居中裁切展示，避免角色图过小。首页固定角色图标使用 Vite 打包后的静态资源导入；后端面试官数据中的岚川头像使用前端 `public/assets/characters/interviewer-idle.png` 对应的稳定 URL `/assets/characters/interviewer-idle.png`，禁止返回 `/src/...` 源码路径，避免生产环境图片 404。
- 创建成功后跳转 `/app/interview/:sessionId`；该页先收集自我介绍、展示岚川反馈，再进入正式题。正式题保留每题最多两层追问，逐题显示评分，结束后可回看介绍、逐题记录和综合复盘。

## 7. 部署与 404 排查

前端 `8088` 是 Nginx 容器，会将 `/api` 代理给后端 `app` 容器。若浏览器仍加载旧的打包文件，或后端镜像构建于面试接口加入之前，会出现 `/api/interviews/interviewers`、`/resumes`、`/records` 的 `404`。

在代码和 OCR 环境变量配置完成后，必须先打包后端 JAR，再重建两个容器：

```powershell
cd D:\xiao-rong\xiaorong-teacher-assistant
mvn -q -DskipTests package
docker compose up -d --build app frontend
docker compose ps
```

登录状态下重新打开 `/app/courses` 并选择“模拟面试”后，三项 GET 请求应该返回 `200`；未携带登录令牌时返回 `401` 是鉴权行为，不是接口缺失。
### 7.1 上传返回 413

前端 Nginx 在 `server` 级配置 `client_max_body_size 10m`，后端在 `spring.servlet.multipart` 下配置 `max-file-size: 10MB` 与 `max-request-size: 10MB`。修改这些配置后必须同时重建 `frontend` 和 `app` 镜像；仅刷新浏览器不会更新容器内的 Nginx 配置。

传输层 10 MB 不代表业务允许上传 10 MB：简历解析器仍执行 5 MB 上限，并返回可读的业务错误。若 5 MB 以内文件仍返回 413，依次检查浏览器请求是否经过当前 `frontend` 容器、容器内 `/etc/nginx/conf.d/default.conf` 是否包含该配置，以及是否还有额外网关/反向代理。
