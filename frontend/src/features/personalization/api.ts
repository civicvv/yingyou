import { api } from '@/shared/api/client'
import type { PracticeSessionView } from '@/features/practice/api'

export const examTracks = [
  { code: 'CET4', label: '四级' },
  { code: 'CET6', label: '六级' },
  { code: 'IELTS_ACADEMIC', label: '雅思学术类' },
  { code: 'TOEFL_IBT', label: '托福 iBT' },
  { code: 'GENERAL', label: '通用基础' },
] as const

export interface LearningPreferences {
  examTrack: string
  vocabBand: string
  autoDifficulty: string
  manualDifficulty: string | null
  effectiveDifficulty: string
  revision: number
  lastAssessmentAt: string | null
}

export interface PersonalizedBatch {
  session: PracticeSessionView
  examTrack: string
  vocabBand: string
  baselineDifficulty: string
  actualCount: number
  reasons: string[]
  fallbackReason: string | null
}

export function fetchPreferences() {
  return api.get<LearningPreferences>('/me/learning-preferences')
}

export function updatePreferences(
  body: {
    requestId: string
    revision: number
    examTrack?: string
    difficultyMode?: 'AUTO' | 'MANUAL'
    manualDifficulty?: string
  },
) {
  return api.patch<LearningPreferences>('/me/learning-preferences', body)
}

export function createPersonalizedSession(body: { requestId: string; examTrack?: string }) {
  return api.post<PersonalizedBatch>('/personalized-practice/sessions', body)
}
