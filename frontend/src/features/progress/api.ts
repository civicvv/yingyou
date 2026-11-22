import { api } from '@/shared/api/client'
import type { ProgressStatus } from '@/shared/api/types'

export interface LessonProgress {
  lessonId: string
  title: string
  sortOrder: number
  questionCount: number
  status: ProgressStatus
  lastActiveSessionId: string | null
  lastCompletedSessionId: string | null
  continueSessionId: string | null
  lastPracticedAt: string | null
  firstCompletedAt: string | null
}

export interface CourseProgress {
  courseId: string
  examTrack: string
  title: string
  summary: string
  lessonCount: number
  completedLessonCount: number
  completionRate: number
  lastPracticedAt: string | null
  lessons: LessonProgress[]
}

export interface MyCourse {
  courseId: string
  examTrack: string
  title: string
  summary: string
  lessonCount: number
  completedLessonCount: number
  completionRate: number
  lastPracticedAt: string | null
  continueLessonId: string | null
  continueSessionId: string | null
  lastCompletedLessonId: string | null
  lastCompletedSessionId: string | null
  lessons: LessonProgress[]
}

export function fetchMyCourses() {
  return api.get<MyCourse[]>('/me/courses')
}

export function fetchCourseProgress(courseId: string) {
  return api.get<CourseProgress>(`/me/progress?courseId=${encodeURIComponent(courseId)}`)
}

export function percentText(rate: number) {
  return `${Math.round(rate * 100)}%`
}

export function formatDateTime(value: string | null) {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export const statusLabels: Record<ProgressStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
}
