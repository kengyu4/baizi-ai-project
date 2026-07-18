# 白子 AI 前端

这是白子 AI 学习与模拟面试平台的 Vue 3 前端工程。

## 技术栈

- Vue 3 + TypeScript
- Vite
- Vue Router + Pinia

## 本地开发

```bash
npm ci
npm run dev
```

开发服务器默认监听 `http://localhost:5173`，并将 `/api`、`/internal` 代理到 `http://localhost:8088`。

## 质量检查

```bash
npm test
npm run type-check
npm run build-only
```

## 容器构建

```bash
docker build -t baizi-ai-frontend .
```

生产镜像通过 Nginx 提供 Vue 构建产物，并将 `/api` 和 `/internal` 反向代理到 Compose 网络内的后端 `app:8088`。