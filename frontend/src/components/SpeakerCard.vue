<script setup lang="ts">
import { computed } from 'vue'
import teacherIdle from '@/assets/characters/teacher-idle.png'
import teacherExplain from '@/assets/characters/teacher-explain.png'
import teacherCorrect from '@/assets/characters/teacher-correct.png'
import baiziIdle from '@/assets/characters/baizi-idle.png'
import baiziAsk from '@/assets/characters/baizi-ask.png'
import baiziHappy from '@/assets/characters/baizi-happy.png'
import baiziComfort from '@/assets/characters/baizi-comfort.png'
import interviewerIdle from '@/assets/characters/interviewer-idle.png'
import interviewerQuestion from '@/assets/characters/interviewer-question.png'
import interviewerScoring from '@/assets/characters/interviewer-scoring.png'

type TeacherPose = 'idle' | 'explain' | 'correct'
type BaiziPose = 'idle' | 'ask' | 'happy' | 'comfort'
type InterviewerPose = 'idle' | 'question' | 'scoring'
type SpeakerType = 'teacher' | 'baizi' | 'interviewer'

const props = defineProps<{
  type: SpeakerType
  pose?: TeacherPose | BaiziPose | InterviewerPose
}>()

const teacherAssets: Record<TeacherPose, string> = {
  idle: teacherIdle,
  explain: teacherExplain,
  correct: teacherCorrect,
}

const baiziAssets: Record<BaiziPose, string> = {
  idle: baiziIdle,
  ask: baiziAsk,
  happy: baiziHappy,
  comfort: baiziComfort,
}

const interviewerAssets: Record<InterviewerPose, string> = {
  idle: interviewerIdle,
  question: interviewerQuestion,
  scoring: interviewerScoring,
}

const labelMap: Record<SpeakerType, { name: string; role: string }> = {
  teacher: { name: '小绒老师', role: 'AI 老师' },
  baizi: { name: '白子同桌', role: '学习伙伴' },
  interviewer: { name: '岚川', role: 'AI 面试官' },
}

const label = computed(() => labelMap[props.type])
const image = computed(() => {
  if (props.type === 'teacher') {
    const pose = (props.pose ?? 'explain') as TeacherPose
    return teacherAssets[pose] ?? teacherExplain
  }
  if (props.type === 'baizi') {
    const pose = (props.pose ?? 'idle') as BaiziPose
    return baiziAssets[pose] ?? baiziIdle
  }
  const pose = (props.pose ?? 'idle') as InterviewerPose
  return interviewerAssets[pose] ?? interviewerIdle
})
</script>

<template>
  <div class="speaker-card" :class="type">
    <img :src="image" :alt="label.name">
    <div class="speaker-name">
      <strong>{{ label.name }}</strong>
      <span>{{ label.role }}</span>
    </div>
  </div>
</template>
