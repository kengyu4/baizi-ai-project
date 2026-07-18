import { apiGet, apiPost, apiPostForm, apiPut } from './http'
import type {
  InterviewAnswerResult,
  InterviewRecord,
  InterviewRecordListItem,
  InterviewReport,
  InterviewResume,
  InterviewSession,
  Interviewer,
  ResumeUploadResult,
} from './types'

export function listInterviewers() { return apiGet<Interviewer[]>('/api/interviews/interviewers') }
export function listResumes() { return apiGet<InterviewResume[]>('/api/interviews/resumes') }
export function createResume(body: { title: string; positionName: string; content: string }) { return apiPost<InterviewResume>('/api/interviews/resumes', body) }
export function updateResume(resumeId: number, body: { title: string; positionName: string; content: string }) { return apiPut<InterviewResume>(`/api/interviews/resumes/${resumeId}`, body) }
export function uploadResume(file: File, positionName: string) {
  const data = new FormData()
  data.append('file', file)
  data.append('positionName', positionName)
  return apiPostForm<ResumeUploadResult>('/api/interviews/resumes/upload', data)
}
export function createInterviewSession(body: { interviewerCode: string; resumeId: number | null; positionName: string; subjectName: string; questionCount: number }) {
  return apiPost<InterviewSession>('/api/interviews/sessions', body)
}
export function getInterviewSession(sessionId: number) { return apiGet<InterviewSession>(`/api/interviews/sessions/${sessionId}`) }
export function submitSelfIntroduction(sessionId: number, content: string) { return apiPost<InterviewSession>(`/api/interviews/sessions/${sessionId}/self-introduction`, { content }) }
export function submitInterviewAnswer(sessionId: number, answerText: string) { return apiPost<InterviewAnswerResult>(`/api/interviews/sessions/${sessionId}/answers`, { answerText }) }
export function getInterviewReport(sessionId: number) { return apiGet<InterviewReport>(`/api/interviews/sessions/${sessionId}/report`) }
export function listInterviewRecords() { return apiGet<InterviewRecordListItem[]>('/api/interviews/records') }
export function getInterviewRecords(sessionId: number) { return apiGet<InterviewRecord[]>(`/api/interviews/records/${sessionId}`) }
