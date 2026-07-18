import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (relativePath: string) => readFile(new URL(relativePath, import.meta.url), 'utf8')

test('岚川卡片使用清晰的大尺寸头像并面向通用求职', async () => {
  const home = await read('../views/HomeView.vue')
  const memoryStore = await read('../../../xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/interview/persistence/MemoryInterviewStore.java')
  const schema = await read('../../../xiaorong-teacher-assistant/src/main/java/com/xiaorong/assistant/interview/persistence/InterviewSchemaInitializer.java')

  assert.match(home, /\.interviewer-choice img\s*\{[^}]*width:\s*88px;[^}]*height:\s*88px;[^}]*object-fit:\s*cover;[^}]*object-position:\s*top center;/s)
  assert.doesNotMatch(home, /当前支持前端开发、Java 后端和通用求职三个方向/)
  assert.match(home, /覆盖全部岗位方向；系统会结合目标岗位与简历内容动态选择题目/)
  assert.match(memoryStore, /"通用求职"/)
  assert.match(memoryStore, /List\.of\("全部岗位"\)/)
  assert.match(schema, /'通用求职'/)
  assert.match(schema, /\[\\"全部岗位\\"\]/)
})

test('PDF 上传链路启用自动 OCR、放宽代理传输并提供 413 可读提示', async () => {
  const home = await read('../views/HomeView.vue')
  const http = await read('http.ts')
  const nginx = await read('../../nginx.conf')
  const application = await read('../../../xiaorong-teacher-assistant/src/main/resources/application.yml')

  assert.match(home, /扫描件 PDF 将自动调用 OCR，识别后请核对文本/)
  assert.match(home, /accept="\.pdf,\.docx,\.txt,application\/pdf"/)
  assert.match(http, /error instanceof ApiError && error\.status === 413[\s\S]*简历文件上传失败：请求体超过代理限制/)
  assert.match(nginx, /client_max_body_size\s+10m;/)
  assert.match(application, /multipart:[\s\S]*max-file-size:\s*10MB[\s\S]*max-request-size:\s*10MB/)
})
test('岚川面试在正式提问前收集自我介绍并保留两层追问', async () => {
  const view = await read('../views/InterviewView.vue')
  const api = await read('interview.ts')
  const types = await read('types.ts')

  assert.match(api, /submitSelfIntroduction/)
  assert.match(api, /\/self-introduction/)
  assert.match(types, /status: 'self_intro_pending' \| 'interviewing' \| 'finished'/)
  assert.match(types, /selfIntroduction: SelfIntroduction \| null/)
  assert.match(view, /selfIntroPending/)
  assert.match(view, /请先做自我介绍/)
  assert.match(view, /submitIntroduction/)
  assert.match(view, /岚川对自我介绍的反馈/)
  assert.match(view, /本题最多追问 2 层/)
  assert.match(view, /ensureLanchuanPrefix/)
  assert.match(view, /喵～/)
})