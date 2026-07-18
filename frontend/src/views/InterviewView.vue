<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getInterviewRecords, getInterviewReport, getInterviewSession, submitInterviewAnswer, submitSelfIntroduction } from '@/api/interview'
import type { InterviewQuestion, InterviewRecord, InterviewReport, InterviewScore, InterviewSession } from '@/api/types'
import Avatar from '@/components/Avatar.vue'
import CollapsiblePanel from '@/components/CollapsiblePanel.vue'
import SpeakerCard from '@/components/SpeakerCard.vue'

const route = useRoute()
const router = useRouter()
const session = ref<InterviewSession | null>(null)
const report = ref<InterviewReport | null>(null)
const records = ref<InterviewRecord[]>([])
const answer = ref('')
const followUpQuestion = ref<string | null>(null)
const pendingQuestion = ref<InterviewQuestion | null>(null)
const lastScore = ref<InterviewScore | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')

const sessionId = computed(() => Number(route.params.sessionId))
const selfIntroPending = computed(() => session.value?.status === 'self_intro_pending')
const interviewFinished = computed(() => session.value?.status === 'finished')
const activeQuestion = computed(() => pendingQuestion.value ?? session.value?.currentQuestion ?? null)
const introductionFeedback = computed(() => session.value?.selfIntroduction?.feedback ?? null)
function ensureLanchuanPrefix(value: string) {
  const content = value.trim()
  return content.startsWith('喵～') ? content : `喵～${content}`
}
const lanchuanIntroductionFeedback = computed(() => introductionFeedback.value ? ensureLanchuanPrefix(introductionFeedback.value.summary) : '')
const lanchuanScoreComment = computed(() => lastScore.value ? ensureLanchuanPrefix(lastScore.value.interviewerComment) : '')
const lanchuanRisk = computed(() => lastScore.value ? ensureLanchuanPrefix(lastScore.value.risk) : '')
const lanchuanReportSummary = computed(() => report.value ? ensureLanchuanPrefix(report.value.summary) : '')
const progressText = computed(() => {
  if (!session.value) return '--'
  if (selfIntroPending.value) return '自我介绍'
  const completed = interviewFinished.value ? session.value.questionCount : session.value.currentQuestionIndex + 1
  return `${completed} / ${session.value.questionCount}`
})
const interviewerPose = computed<'idle' | 'question' | 'scoring'>(() => {
  if (interviewFinished.value || submitting.value || lastScore.value) return 'scoring'
  return followUpQuestion.value ? 'question' : 'idle'
})
const inputTitle = computed(() => {
  if (selfIntroPending.value) return '请先做自我介绍'
  return followUpQuestion.value ? `第 ${activeQuestion.value?.followUpLevel ?? 1} 层追问回答` : '你的回答'
})
const inputPlaceholder = computed(() => {
  if (selfIntroPending.value) return '请介绍岗位意向、近期经历、项目或实习、个人职责和取得的结果。'
  return followUpQuestion.value
    ? '请结合刚才的观点继续回答，不需要重复整段主回答。'
    : '请按“结论 → 原因 → 场景”的结构作答。'
})
const submitLabel = computed(() => {
  if (submitting.value) return selfIntroPending.value ? '岚川正在准备首题...' : '岚川正在分析...'
  if (selfIntroPending.value) return '提交自我介绍，开始面试'
  return followUpQuestion.value ? '提交追问回答' : '提交回答'
})
const interviewText = computed(() => {
  if (!session.value) return loading.value ? '正在加载面试会话…' : '未找到面试会话。'
  let content = ''
  if (selfIntroPending.value) content = session.value.opening
  else if (interviewFinished.value) content = report.value?.summary ?? '本轮模拟面试已结束，正在生成复盘报告。'
  else if (followUpQuestion.value) content = followUpQuestion.value
  else if (!activeQuestion.value) content = '正在准备下一题，请稍候。'
  else content = `第 ${activeQuestion.value.questionIndex} 题：\n${activeQuestion.value.topicName}\n提示：${activeQuestion.value.keyHint}\n请开始你的回答。`
  return ensureLanchuanPrefix(content)
})
const interviewHint = computed(() => {
  if (selfIntroPending.value) return '自我介绍不计入正式题数和总分；岚川会先给出反馈，再从你的真实经历开始提问。'
  if (interviewFinished.value) return '本轮评分、风险点与建议均已保存到面试记录，可随时回看。'
  if (followUpQuestion.value) return `当前为第 ${activeQuestion.value?.followUpLevel ?? 1} 层追问。本题最多追问 2 层。`
  return '岚川会根据你的回答继续追问，最多两层；请尽量给出清晰、可验证的表达。'
})

onMounted(() => { void loadInterview() })

async function loadInterview() {
  if (!Number.isSafeInteger(sessionId.value) || sessionId.value <= 0) {
    errorMessage.value = '无效的面试会话编号。'
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    session.value = await getInterviewSession(sessionId.value)
    if (session.value.status === 'finished') await loadReviewData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '面试会话加载失败'
  } finally {
    loading.value = false
  }
}

async function loadReviewData() {
  const [nextReport, nextRecords] = await Promise.all([
    getInterviewReport(sessionId.value),
    getInterviewRecords(sessionId.value),
  ])
  report.value = nextReport
  records.value = nextRecords
}

async function submitIntroduction() {
  if (!answer.value.trim() || !session.value || !selfIntroPending.value || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    session.value = await submitSelfIntroduction(sessionId.value, answer.value.trim())
    answer.value = ''
    followUpQuestion.value = null
    pendingQuestion.value = null
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交自我介绍失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function submitAnswer() {
  if (selfIntroPending.value) {
    await submitIntroduction()
    return
  }
  if (!answer.value.trim() || !session.value || interviewFinished.value || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const result = await submitInterviewAnswer(sessionId.value, answer.value.trim())
    answer.value = ''
    lastScore.value = result.score
    if (result.action === 'follow_up') {
      pendingQuestion.value = result.currentQuestion
      followUpQuestion.value = result.followUpQuestion
      return
    }
    followUpQuestion.value = null
    pendingQuestion.value = null
    session.value = await getInterviewSession(sessionId.value)
    if (result.finished || session.value.status === 'finished') await loadReviewData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交回答失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function returnToSetup() {
  router.push('/app/courses')
}
</script>

<template>
  <div class="container interview-page">
    <section class="study-layout">
      <main>
        <section class="lesson-card">
          <div class="lesson-head">
            <div>
              <div class="eyebrow interview-eyebrow">岚川 · 真实模拟面试</div>
              <h1 class="lesson-title">{{ session?.positionName ?? '模拟面试' }}</h1>
              <p class="lesson-desc">先完成自我介绍，岚川再结合岗位和真实经历自由提问；每道正式题最多两层追问，评分与复盘会保存到面试记录。</p>
            </div>
            <span class="chip" :class="interviewFinished ? 'mint' : 'warn'">{{ interviewFinished ? '面试完成' : progressText }}</span>
          </div>

          <div v-if="loading" class="panel inline-state">正在加载真实面试会话…</div>
          <div v-else-if="errorMessage && !session" class="panel inline-state error-state">
            <p>{{ errorMessage }}</p>
            <div class="action-row"><button class="btn" type="button" @click="loadInterview">重新加载</button><button class="btn secondary" type="button" @click="returnToSetup">返回配置</button></div>
          </div>

          <template v-else-if="session">
            <CollapsiblePanel title="岚川面试官" :start-open="true" :reset-key="`${session.status}:${session.currentQuestionIndex}:${followUpQuestion}:${interviewFinished}`">
              <div class="dialogue-layout">
                <SpeakerCard type="interviewer" :pose="interviewerPose" />
                <div class="dialogue-box">
                  <div class="dialogue-meta">
                    <span class="meta-name"><Avatar type="interviewer" :size="28" />{{ session.interviewer.name }} · 模拟面试官</span>
                    <span>{{ interviewFinished ? '面试复盘' : selfIntroPending ? '自我介绍' : followUpQuestion ? `第 ${activeQuestion?.followUpLevel ?? 1} 层追问` : '正式提问' }}</span>
                  </div>
                  <div class="dialogue-text">{{ interviewText }}</div>
                  <div class="dialogue-tip">{{ interviewHint }}</div>
                </div>
              </div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="!interviewFinished" :title="inputTitle" :start-open="true" :reset-key="`${session.status}:${session.currentQuestionIndex}:${followUpQuestion}:answer`">
              <div class="answer-box">
                <strong>{{ inputTitle }}</strong>
                <textarea v-model="answer" :disabled="submitting" :placeholder="inputPlaceholder" @keydown.ctrl.enter.prevent="submitAnswer" />
                <div v-if="!selfIntroPending && activeQuestion" class="keyword-row">
                  <span v-for="keyword in activeQuestion.keywords" :key="keyword" class="keyword">{{ keyword }}</span>
                </div>
                <div class="action-row"><button class="btn" type="button" :disabled="!answer.trim() || submitting" @click="submitAnswer">{{ submitLabel }}</button></div>
                <p class="shortcut-hint">按 Ctrl + Enter 可快捷提交。</p>
              </div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="introductionFeedback" title="岚川对自我介绍的反馈" :start-open="true" :reset-key="`${session.sessionId}:${introductionFeedback.summary}`">
              <div class="interview-result">
                <p>{{ lanchuanIntroductionFeedback }}</p>
                <div v-if="introductionFeedback.strengths.length" class="keyword-row"><span v-for="item in introductionFeedback.strengths" :key="`strength-${item}`" class="keyword hit">{{ item }}</span></div>
                <div v-if="introductionFeedback.gaps.length" class="keyword-row"><span v-for="item in introductionFeedback.gaps" :key="`gap-${item}`" class="keyword">{{ item }}</span></div>
              </div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="lastScore" title="刚完成题目的评分" :start-open="true" :reset-key="`${session.currentQuestionIndex}:${lastScore.score}:${lastScore.interviewerComment}`">
              <div class="result-box interview-result">
                <div class="dialogue-meta"><span>岚川评分 · {{ lastScore.level }}</span><span class="score">{{ lastScore.score }}</span></div>
                <p>{{ lanchuanScoreComment }}</p>
                <div class="interview-risk"><strong>风险点</strong><span>{{ lanchuanRisk }}</span></div>
                <div class="keyword-row"><span v-for="keyword in lastScore.hitKeywords" :key="`hit-${keyword}`" class="keyword hit">{{ keyword }}</span><span v-for="keyword in lastScore.missKeywords" :key="`miss-${keyword}`" class="keyword">{{ keyword }}</span></div>
              </div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="interviewFinished && report" title="本轮面试复盘" :start-open="true" reset-key="interview-review">
              <div class="interview-review">
                <div class="interview-score"><span>综合评分</span><strong>{{ report.overallScore }}</strong><em>{{ report.level }}</em></div>
                <div><strong>总结</strong><p>{{ lanchuanReportSummary }}</p></div>
                <div v-if="report.weakTags.length"><strong>待提升标签</strong><div class="keyword-row"><span v-for="tag in report.weakTags" :key="tag" class="keyword">{{ tag }}</span></div></div>
                <div><strong>建议</strong><ul><li v-for="item in report.suggestions" :key="item">{{ item }}</li></ul></div>
                <div v-if="report.recommendedCourses.length"><strong>推荐复习</strong><div class="keyword-row"><span v-for="course in report.recommendedCourses" :key="course" class="chip mint">{{ course }}</span></div></div>
                <div class="action-row"><button class="btn" type="button" @click="returnToSetup">再来一轮</button><button class="btn secondary" type="button" @click="router.push('/app/courses')">返回课程</button></div>
              </div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="interviewFinished && session.selfIntroduction" title="自我介绍与岚川反馈" :start-open="false" reset-key="interview-introduction-replay">
              <div class="record-card"><strong>候选人自我介绍</strong><p>{{ session.selfIntroduction.content }}</p><span v-if="introductionFeedback">{{ lanchuanIntroductionFeedback }}</span></div>
            </CollapsiblePanel>

            <CollapsiblePanel v-if="interviewFinished && records.length" title="答题记录" :start-open="false" reset-key="interview-records">
              <div class="record-list"><article v-for="item in records" :key="item.recordId" class="record-card"><strong>第 {{ item.questionIndex }} 题 · {{ item.topicName }}</strong><span v-if="item.score !== null">{{ item.score }} 分 · {{ item.level }}</span><p>{{ item.userAnswer }}</p><small v-if="item.followUpQuestions.length">追问 {{ item.followUpQuestions.length }} 次</small></article></div>
            </CollapsiblePanel>
          </template>
        </section>
      </main>

      <aside class="side-panel">
        <section class="panel interview-side-card"><h2>本轮规则</h2><div class="timeline"><div class="timeline-item active"><strong>自我介绍先行</strong><br>反馈不计入正式题数和总分。</div><div class="timeline-item"><strong>{{ session?.questionCount ?? 3 }} 道自由岗位题</strong><br>优先围绕真实项目或实习经历提问。</div><div class="timeline-item"><strong>最多 2 层追问</strong><br>围绕刚刚的回答核验贡献、取舍和结果。</div><div class="timeline-item"><strong>评分与复盘留存</strong><br>介绍、单题记录、综合报告均可回看。</div></div></section>
        <section class="panel interview-side-card"><h2>当前配置</h2><div class="side-info"><span>面试官</span><strong>{{ session?.interviewer.name ?? '岚川' }}</strong><span>岗位</span><strong>{{ session?.positionName ?? '--' }}</strong><span>简历</span><strong>{{ session?.resume?.title ?? '未关联简历' }}</strong></div></section>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.interview-page { padding-bottom: 24px; }
.interview-eyebrow { color: #4d5567; background: #eef2f7; }
.inline-state { margin-top: 16px; padding: 20px; color: var(--muted); }.error-state { color: #b42318; }.error-state p { margin: 0; }
.interview-result, .interview-review { display: grid; gap: 14px; }.interview-result p, .interview-review p { margin: 0; color: #465762; line-height: 1.75; }
.interview-risk { display: grid; gap: 4px; border-radius: 14px; padding: 12px; color: #7c4a03; background: #fff8e9; border: 1px solid #f4dfae; line-height: 1.6; font-size: 13px; }
.interview-score { display: flex; align-items: baseline; gap: 10px; }.interview-score span, .interview-score em { color: var(--muted); font-style: normal; font-weight: 800; }.interview-score strong { color: var(--primary-strong); font-size: 42px; line-height: 1; }
.interview-review ul { margin: 8px 0 0; padding-left: 20px; color: #465762; line-height: 1.8; }.shortcut-hint { margin: 0; color: var(--muted); font-size: 12px; }
.side-info { display: grid; grid-template-columns: 70px 1fr; gap: 9px; color: var(--muted); font-size: 13px; }.side-info strong { color: var(--ink); }
.record-list { display: grid; gap: 10px; }.record-card { display: grid; gap: 6px; padding: 12px; border: 1px solid #dce4ea; border-radius: 14px; }.record-card span, .record-card small { color: var(--muted); font-size: 12px; }.record-card p { margin: 0; color: #465762; line-height: 1.6; }
@media (max-width: 720px) { .interview-score strong { font-size: 36px; } }
</style>