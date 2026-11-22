import { api } from '@/shared/api/client'

export interface ExplanationSegment {
  text: string
  role: string
  gloss: string
}

export interface NextPreview {
  position: number
  promptZh: string
}

export interface PracticeItemView {
  id: string
  promptZh: string
  contextZh: string | null
  status: 'OPEN' | 'PASSED_INDEPENDENT' | 'PASSED_ASSISTED' | 'SKIPPED'
  hintUsed: boolean
  answerRevealed: boolean
  wordLengths: number[]
}

export interface PracticeFeedbackView {
  answer: string | null
  explanationZh: string | null
  explanationSegments: ExplanationSegment[] | null
  grammarTags: string[] | null
  matchedCount: number | null
  firstMismatchIndex: number | null
  differenceType: string | null
  hint: string | null
  nextPreview: NextPreview | null
}

export interface PracticeSessionView {
  id: string
  status: 'OPEN' | 'COMPLETED'
  revision: number
  position: number
  total: number
  courseId: string | null
  lessonId: string | null
  lessonTitle: string | null
  currentItem: PracticeItemView | null
  feedback: PracticeFeedbackView | null
}

export interface PracticeCommandResponse {
  session: PracticeSessionView
  attemptId: string | null
}

export interface PrefixCheckResponse {
  revision: number
  itemId: string
  matchedCount: number
  firstMismatchIndex: number | null
}

export interface PracticeResultItem {
  position: number
  promptZh: string
  canonicalEn: string
  status: string
  hintUsed: boolean
  answerRevealed: boolean
  attemptCount: number
  firstTryCorrect: boolean
}

export interface PracticeResult {
  sessionId: string
  courseId: string | null
  lessonId: string | null
  lessonVersionId: string | null
  lessonTitle: string | null
  total: number
  independentCount: number
  assistedCount: number
  skippedCount: number
  firstTryCorrectCount: number
  firstTryAccuracy: number
  items: PracticeResultItem[]
}

export type PracticeAction = 'SUBMIT' | 'HINT' | 'REVEAL' | 'SKIP' | 'NEXT'

export function createPracticeSession(lessonId: string, requestId: string) {
  return api.post<PracticeSessionView>('/practice/sessions', { lessonId, requestId })
}

export function fetchPracticeSession(sessionId: string) {
  return api.get<PracticeSessionView>(`/practice/sessions/${sessionId}`)
}

export function checkPrefix(
  sessionId: string,
  body: { revision: number; itemId: string; text: string },
) {
  return api.post<PrefixCheckResponse>(`/practice/sessions/${sessionId}/prefix-check`, body)
}

export function sendCommand(
  sessionId: string,
  body: {
    requestId: string
    revision: number
    itemId: string
    action: PracticeAction
    text?: string
    wordIndex?: number
  },
) {
  return api.post<PracticeCommandResponse>(`/practice/sessions/${sessionId}/commands`, body)
}

export function fetchPracticeResult(sessionId: string) {
  return api.get<PracticeResult>(`/practice/sessions/${sessionId}/result`)
}
