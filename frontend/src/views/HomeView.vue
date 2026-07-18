<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  createInterviewSession,
  createResume,
  listInterviewRecords,
  listInterviewers,
  listResumes,
  updateResume,
  uploadResume,
} from '@/api/interview'
import type { InterviewRecordListItem, InterviewResume, Interviewer } from '@/api/types'
import CourseCard from '@/components/CourseCard.vue'
import teacherExplainImg from '@/assets/characters/teacher-explain.png'
import baiziAskImg from '@/assets/characters/baizi-ask.png'
import interviewerQuestionImg from '@/assets/characters/interviewer-question.png'
import baiziHappyImg from '@/assets/characters/baizi-happy.png'
import { useStudyStore } from '@/stores/study'

const store = useStudyStore()
const router = useRouter()
const selectedMode = ref<'immersive' | 'interview'>('immersive')
const interviewers = ref<Interviewer[]>([])
const resumes = ref<InterviewResume[]>([])
const records = ref<InterviewRecordListItem[]>([])
const selectedInterviewerCode = ref('lanchuan')
const selectedResumeId = ref<number | null>(null)
const interviewLoading = ref(false)
const savingResume = ref(false)
const uploadingResume = ref(false)
const interviewError = ref('')
const resumeNotice = ref('')
const resumeEditor = reactive({ title: '我的求职简历', positionName: '', content: '' })
const firstCourseId = computed(() => store.courses.find((course) => course.lessonCount > 0)?.courseId)
const resumeSaveLabel = computed(() => {
  if (savingResume.value) return '保存中...'
  return selectedResumeId.value === null ? '保存为新简历' : '保存修改'
})

onMounted(() => {
  if (!store.courses.length) store.loadCourses()
  store.loadOverview().catch(() => undefined)
  store.loadOverviewAdvice().catch(() => undefined)
})

watch(() => store.session, (session) => {
  if (session) router.push(`/app/classroom/${session.sessionId}`)
})

function selectMode(mode: 'immersive' | 'interview') {
  selectedMode.value = mode
  if (mode === 'interview') void loadInterviewSetup()
}

function refreshAdvice() {
  store.loadOverviewAdvice().catch(() => undefined)
}

async function loadInterviewSetup() {
  interviewLoading.value = true
  interviewError.value = ''
  try {
    const [people, items, history] = await Promise.all([
      listInterviewers(),
      listResumes(),
      listInterviewRecords(),
    ])
    interviewers.value = people
    resumes.value = items
    records.value = history
    if (people.length && !people.some((item) => item.code === selectedInterviewerCode.value)) {
      selectedInterviewerCode.value = people[0]!.code
    }
    if (selectedResumeId.value === null && items.length) selectResume(items[0]!)
  } catch (error) {
    interviewError.value = error instanceof Error ? error.message : '面试配置加载失败'
  } finally {
    interviewLoading.value = false
  }
}

function selectResume(resume: InterviewResume) {
  selectedResumeId.value = resume.resumeId
  resumeEditor.title = resume.title
  resumeEditor.positionName = resume.positionName
  resumeEditor.content = resume.content
}

async function saveResume() {
  if (!resumeEditor.content.trim()) {
    interviewError.value = '请粘贴简历内容，或先上传 PDF、DOCX、TXT 文件。'
    return
  }
  savingResume.value = true
  interviewError.value = ''
  try {
    const payload = { ...resumeEditor, content: resumeEditor.content.trim() }
    const saved = selectedResumeId.value === null
      ? await createResume(payload)
      : await updateResume(selectedResumeId.value, payload)
    const index = resumes.value.findIndex((item) => item.resumeId === saved.resumeId)
    if (index === -1) resumes.value = [saved, ...resumes.value]
    else resumes.value.splice(index, 1, saved)
    selectedResumeId.value = saved.resumeId
  } catch (error) {
    interviewError.value = error instanceof Error ? error.message : '简历保存失败'
  } finally {
    savingResume.value = false
  }
}

async function parseResume(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingResume.value = true
  interviewError.value = ''
  resumeNotice.value = ''
  try {
    const parsed = await uploadResume(file, resumeEditor.positionName)
    resumeEditor.title = parsed.title
    resumeEditor.positionName = parsed.positionName
    resumeEditor.content = parsed.content
    selectedResumeId.value = null
    resumeNotice.value = parsed.parseError ?? '简历解析成功，请核对文本后保存。'
  } catch (error) {
    interviewError.value = error instanceof Error ? error.message : '简历解析失败'
  } finally {
    uploadingResume.value = false
    input.value = ''
  }
}

async function startInterview() {
  interviewError.value = ''
  if (selectedResumeId.value === null) {
    interviewError.value = '请先保存并选择一份简历，再开始模拟面试。'
    return
  }
  try {
    const session = await createInterviewSession({
      interviewerCode: selectedInterviewerCode.value,
      resumeId: selectedResumeId.value,
      positionName: resumeEditor.positionName || '通用岗位',
      subjectName: resumeEditor.positionName || '通用岗位',
      questionCount: 3,
    })
    await router.push(`/app/interview/${session.sessionId}`)
  } catch (error) {
    interviewError.value = error instanceof Error ? error.message : '创建模拟面试失败'
  }
}

function startCourse(courseId: number) {
  if (!courseId) return
  store.startCourse(courseId, 'immersive').catch((error: Error) => alert(error.message))
}
</script>

<template>
  <div class="container">
    <section class="hero">
      <div class="hero-copy">
        <div>
          <div class="eyebrow">AI 学习课堂 · {{ selectedMode === 'immersive' ? '沉浸学习' : '岚川模拟面试' }}</div>
          <h1 class="hero-title">{{ selectedMode === 'immersive' ? '把题库变成会讲课、会提问、会复盘的 AI 课堂' : '把简历与岗位要求变成一场可复盘的真实模拟面试' }}</h1>
          <p class="hero-desc">{{ selectedMode === 'immersive' ? '小绒老师先讲知识点，白子同桌在课间向你请教。答完后直接进入作业，提交一题就讲评一题。' : '岚川会围绕你的岗位和简历逐题提问，最多两层追问；完成后自动保存评分、风险点和复盘建议。' }}</p>
        </div>
        <div class="hero-actions">
          <button v-if="selectedMode === 'immersive'" class="btn" :disabled="!firstCourseId || store.loading" type="button" @click="startCourse(firstCourseId ?? 0)">开始学习</button>
          <button v-else class="btn" :disabled="interviewLoading" type="button" @click="startInterview">开始模拟面试</button>
        </div>
      </div>

      <div class="hero-visual">
        <div class="visual-top"><span class="chip">真实后端会话</span><span class="chip mint">{{ selectedMode === 'immersive' ? '沉浸模式' : '文本简历解析' }}</span></div>
        <div v-if="selectedMode === 'immersive'" class="character-stage">
          <div class="character-card"><img :src="teacherExplainImg" alt="小绒老师"><div class="character-label"><strong>小绒老师</strong><span>讲课、提问、纠错、作业讲评</span></div></div>
          <div class="character-card baizi"><img :src="baiziAskImg" alt="白子同桌"><div class="character-label"><strong>白子同桌</strong><span>请教你、鼓励你、一起复盘</span></div></div>
        </div>
        <div v-else class="character-stage interviewer-stage">
          <div class="character-card interviewer-card"><img :src="interviewerQuestionImg" alt="岚川面试官"><div class="character-label"><strong>岚川面试官</strong><span>追问、评分、复盘</span></div></div>
        </div>
        <div class="mini-dialogs">
          <p v-if="selectedMode === 'immersive'">小绒老师：先选一个专题，我会把题目拆成几个容易理解的小节点。</p>
          <p v-else>岚川：请使用 STAR 或“结论—依据—案例”结构作答，我会根据你的回答继续追问。</p>
        </div>
      </div>
    </section>

    <section class="stats-grid">
      <div class="stat-card"><span>课程数量</span><strong>{{ store.courses.length || '--' }}</strong></div>
      <div class="stat-card"><span>今日进度</span><strong>{{ store.overview?.completedCount ?? 0 }}</strong></div>
      <div class="stat-card"><span>薄弱点</span><strong>{{ store.overview?.topWeakTag || '暂无' }}</strong><small v-if="store.overview?.weakTagCount">{{ store.overview.weakTagCount }} 个待巩固标签</small></div>
      <div class="stat-card"><span>{{ selectedMode === 'immersive' ? '协作值' : '面试记录' }}</span><strong>{{ selectedMode === 'immersive' ? '12' : records.length }}</strong></div>
    </section>

    <section v-if="selectedMode === 'immersive'" class="section advice-section">
      <div class="section-head"><div><h2>小绒老师的学习建议</h2><p>根据你的薄弱标签、历史得分和最近课程生成 2–3 条建议。</p></div><button class="btn secondary" :disabled="store.overviewAdviceLoading" type="button" @click="refreshAdvice">{{ store.overviewAdviceLoading ? '刷新中…' : '刷新建议' }}</button></div>
      <div v-if="store.overviewAdviceLoading && !store.overviewAdvice" class="panel"><p class="empty-state">正在整理你的学习记录…</p></div>
      <div v-else-if="store.overviewAdviceError && !store.overviewAdvice" class="panel"><p class="empty-state">{{ store.overviewAdviceError }}</p><button class="btn" type="button" @click="refreshAdvice">重新加载</button></div>
      <div v-else-if="store.overviewAdvice" class="panel advice-panel">
        <div class="dialogue-meta"><span>{{ store.overviewAdvice.teacherSummary }}</span><span class="chip" :class="store.overviewAdvice.degraded ? 'sand' : 'mint'">{{ store.overviewAdvice.degraded || store.overviewAdvice.providerCode === 'mock' ? '演示 / 降级建议' : `真实 AI · ${store.overviewAdvice.providerCode} · ${store.overviewAdvice.model}` }}</span></div>
        <p v-if="!store.overviewAdvice.hasLearningData" class="empty-state">还没有学习数据。完成一次课堂或作业后，我会给出更有针对性的建议。</p>
        <div v-else class="record-list"><div v-for="(item, index) in (store.overviewAdvice?.suggestions ?? []).slice(0, 3)" :key="index" class="record-item"><strong>建议 {{ index + 1 }}</strong><p>{{ item }}</p></div></div>
        <div v-if="store.overviewAdvice.weakTags.length" class="keyword-row"><span v-for="tag in store.overviewAdvice.weakTags" :key="tag" class="tag">{{ tag }}</span></div>
      </div>
    </section>

    <section class="section">
      <div class="section-head"><div><h2>选择学习模式</h2><p>切换模式只改变当前配置区域，不会直接创建或跳转会话。</p></div></div>
      <div class="mode-grid">
        <button class="mode-card" :class="{ active: selectedMode === 'immersive' }" type="button" @click="selectMode('immersive')"><div class="mode-icon">伴</div><div class="mode-copy"><strong>沉浸模式</strong><span>小绒老师讲课，白子同桌一起复盘。</span></div></button>
        <button class="mode-card interview-mode" :class="{ active: selectedMode === 'interview' }" type="button" @click="selectMode('interview')"><div class="mode-icon">面</div><div class="mode-copy"><strong>模拟面试</strong><span>岚川逐题提问，最多两层追问并给出评分。</span></div></button>
      </div>
    </section>

    <section v-if="selectedMode === 'immersive'" class="section">
      <div class="section-head"><div><h2>推荐课程</h2><p>点击开始学习会调用后端创建 session，然后拉取课程脚本。</p></div><span class="chip">GET /api/study/courses</span></div>
      <div v-if="store.loading" class="panel"><p class="empty-state">课程加载中...</p></div>
      <div v-else-if="store.error" class="panel"><p class="empty-state">{{ store.error }}</p><button class="btn" type="button" @click="store.loadCourses()">重新加载</button></div>
      <div v-else-if="!store.courses.length" class="panel"><p class="empty-state">暂时没有可学习课程。</p></div>
      <div v-else class="course-grid"><CourseCard v-for="(course, index) in store.courses" :key="course.courseId" :course="course" :index="index" :disabled="course.lessonCount === 0 || !!store.startingCourseId" :loading="store.startingCourseId === course.courseId" @start="startCourse" /></div>
    </section>

    <section v-else class="section">
      <div class="section-head"><div><h2>配置模拟面试</h2><p>选择岚川并填写任意目标岗位，上传或粘贴简历后创建真实面试会话。文件只在后端解析成文本，不依赖多模态模型。</p></div><span class="chip">/api/interviews</span></div>
      <div v-if="interviewLoading" class="panel"><p class="empty-state">正在加载面试官、简历和历史记录…</p></div>
      <div v-else class="interview-config-grid">
        <article class="panel setup-panel">
          <h3>1. 选择面试官</h3>
          <div v-if="interviewers.length" class="interviewer-list"><button v-for="person in interviewers" :key="person.code" class="interviewer-choice" :class="{ selected: selectedInterviewerCode === person.code }" type="button" @click="selectedInterviewerCode = person.code"><img :src="person.avatar" :alt="person.name"><span><strong>{{ person.name }}</strong><small>{{ person.specialty }}</small><em>{{ person.style }}</em></span></button></div>
          <p v-else class="empty-state">暂无可用面试官。</p>
          <h3>2. 选择已有简历</h3>
          <div v-if="resumes.length" class="resume-list"><button v-for="resume in resumes" :key="resume.resumeId" class="resume-choice" :class="{ selected: selectedResumeId === resume.resumeId }" type="button" @click="selectResume(resume)"><strong>{{ resume.title }}</strong><span>{{ resume.positionName }}</span></button></div>
          <p v-else class="empty-state">还没有已保存的简历，可在右侧上传或粘贴后保存。</p>
        </article>
        <article class="panel setup-panel">
          <h3>3. 上传、确认简历</h3>
          <p class="empty-state">支持 PDF、DOCX、TXT（≤ 5 MB）；扫描件 PDF 将自动调用 OCR，识别后请核对文本。</p>
          <label class="upload-btn btn secondary"><input type="file" accept=".pdf,.docx,.txt,application/pdf" @change="parseResume">{{ uploadingResume ? '正在解析...' : '上传简历文件' }}</label>
          <label>目标岗位<input v-model="resumeEditor.positionName" maxlength="100" placeholder="例如：前端工程师"></label>
          <label>简历标题<input v-model="resumeEditor.title" maxlength="150" placeholder="例如：2026 校招前端简历"></label>
          <label>简历文本<textarea v-model="resumeEditor.content" rows="10" placeholder="上传后会自动填入解析文本；也可以直接粘贴。"></textarea></label>
          <div class="action-row"><button class="btn secondary" :disabled="savingResume" type="button" @click="saveResume">{{ resumeSaveLabel }}</button><button class="btn" :disabled="!interviewers.length" type="button" @click="startInterview">开始模拟面试</button></div>
          <p v-if="resumeNotice" class="form-notice">{{ resumeNotice }}</p>
          <p v-if="interviewError" class="form-error">{{ interviewError }}</p>
        </article>
      </div>
      <div class="panel interview-bank-panel"><h3>本轮面试题库与流程</h3><div class="record-list"><div class="record-item"><strong>岗位题库</strong><p>覆盖全部岗位方向；系统会结合目标岗位与简历内容动态选择题目。</p></div><div class="record-item"><strong>追问状态机</strong><p>每道题先收集主回答，必要时最多两层追问，随后进行单题评分并推进下一题。</p></div><div class="record-item"><strong>可回看记录</strong><p>完成后保存题目、答案、追问、评分、风险点和整体复盘。</p></div></div></div>
      <div v-if="records.length" class="panel history-panel"><h3>最近面试记录</h3><div class="record-list"><div v-for="record in records.slice(0, 3)" :key="record.sessionId" class="record-item"><strong>{{ record.positionName }} · {{ record.interviewerName }}</strong><span>{{ record.status === 'finished' ? `${record.totalScore} 分` : '进行中' }}</span></div></div></div>
    </section>

    <section v-if="selectedMode === 'immersive'" class="section persona-grid">
      <div class="persona-card"><div class="avatar-mini"><img :src="teacherExplainImg" alt="小绒老师"></div><div><strong>小绒老师</strong><p>课堂节点来自后端脚本，讲完后会进入作业。</p></div></div>
      <div class="persona-card"><div class="avatar-mini"><img :src="baiziHappyImg" alt="白子同桌"></div><div><strong>白子同桌</strong><p>沉浸模式下会出现同桌互助，答对会增加协作值。</p></div></div>
    </section>
  </div>
</template>

<style scoped>
.interview-config-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr); gap: 14px; }
.setup-panel, .interview-bank-panel, .history-panel { display: grid; gap: 12px; padding: 18px; }
.setup-panel h3, .interview-bank-panel h3, .history-panel h3 { margin: 0; color: var(--primary-strong); font-size: 17px; }
.setup-panel label { display: grid; gap: 6px; color: #536170; font-size: 13px; font-weight: 800; }
.setup-panel input, .setup-panel textarea { width: 100%; box-sizing: border-box; border: 1px solid #ccd6dd; border-radius: 12px; padding: 10px 12px; color: var(--ink); font: inherit; background: #fff; }
.setup-panel textarea { resize: vertical; line-height: 1.6; }
.upload-btn { width: fit-content; cursor: pointer; }.upload-btn input { display: none; }
.interviewer-list, .resume-list { display: grid; gap: 8px; }
.interviewer-choice, .resume-choice { width: 100%; border: 1px solid #dce4ea; border-radius: 14px; padding: 10px; background: #fff; color: var(--ink); text-align: left; cursor: pointer; }
.interviewer-choice { display: flex; align-items: center; gap: 14px; }.interviewer-choice img { width: 88px; height: 88px; flex: 0 0 88px; border-radius: 16px; background: #f7fbfc; object-fit: cover; object-position: top center; }.interviewer-choice span { display: grid; gap: 4px; }
.interviewer-choice small, .interviewer-choice em, .resume-choice span { color: var(--muted); font-size: 12px; font-style: normal; }.resume-choice { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.selected { border-color: #8ac5c5 !important; background: #edf9f6 !important; }.form-notice { margin: 0; color: #18794e; font-size: 13px; }.form-error { margin: 0; color: #b42318; font-size: 13px; }.history-panel { margin-top: 14px; }.interview-bank-panel { margin-top: 14px; }
.interviewer-stage { justify-content: center; }.interviewer-card { max-width: 270px; }.interviewer-card img { object-fit: contain; }
@media (max-width: 820px) { .interview-config-grid { grid-template-columns: 1fr; } }
</style>
