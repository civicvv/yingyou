export interface AuthSession {
  kind: 'ANONYMOUS' | 'GUEST' | 'ACCOUNT'
  learnerId: string | null
  csrfToken: string
}

export interface CourseSummary {
  id: string
  examTrack: string
  title: string
  summary: string
  lessonCount: number
}

export type ProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'

export interface Lesson {
  id: string
  title: string
  sortOrder: number
  questionCount: number
  progressStatus: ProgressStatus
  lastActiveSessionId: string | null
  lastCompletedSessionId: string | null
  continueSessionId: string | null
}

export interface CourseDetail {
  id: string
  examTrack: string
  title: string
  summary: string
  lessons: Lesson[]
}
