import { api } from '@/shared/api/client'

export interface AssessmentOption {
  id: string
  label: string
}

export interface AssessmentItem {
  id: string
  position: number
  word: string
  promptZh: string
  contextEn: string
  options: AssessmentOption[]
}

export interface AssessmentSession {
  id: string
  status: 'OPEN' | 'COMPLETED'
  position: number
  total: number
  revision: number
  answeredCount: number
  currentItem: AssessmentItem | null
}

export interface BandStat {
  band: number
  correct: number
  unknown: number
  total: number
  correctedRatio: number
}

export interface AssessmentResult {
  sessionId: string
  vocabBand: 'V1' | 'V2' | 'V3' | 'V4'
  estimatedFamilies: number | null
  qualitySignal: 'STANDARD' | 'AT_CEILING' | 'UNSTABLE'
  atCeiling: boolean
  unstable: boolean
  algorithmVersion: string
  completedAt: string
  bandStats: BandStat[]
}

export interface AssessmentHistoryItem {
  sessionId: string
  vocabBand: string
  qualitySignal: string
  completedAt: string
}

export function createAssessment(requestId: string) {
  return api.post<AssessmentSession>('/vocabulary-assessments', { requestId })
}

export function fetchAssessment(sessionId: string) {
  return api.get<AssessmentSession>(`/vocabulary-assessments/${sessionId}`)
}

export function submitAssessmentAnswer(
  sessionId: string,
  body: {
    requestId: string
    revision: number
    itemId: string
    optionId?: string
    unknown: boolean
  },
) {
  return api.post<AssessmentSession>(`/vocabulary-assessments/${sessionId}/answers`, body)
}

export function fetchAssessmentResult(sessionId: string) {
  return api.get<AssessmentResult>(`/vocabulary-assessments/${sessionId}/result`)
}

export function fetchAssessmentHistory() {
  return api.get<AssessmentHistoryItem[]>('/me/vocabulary-assessments')
}

export const vocabBandLabel: Record<string, string> = {
  V1: '高频词汇起步',
  V2: '扩展基础搭配',
  V3: '增加主题表达',
  V4: '尝试高级表达',
}

export function qualitySignalLabel(signal: string) {
  if (signal === 'AT_CEILING') return '接近本测试覆盖上限'
  if (signal === 'UNSTABLE') return '各频段表现波动较大'
  return '结果稳定'
}
