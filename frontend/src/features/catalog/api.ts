import { api } from '@/shared/api/client'
import type { CourseDetail, CourseSummary } from '@/shared/api/types'

export function fetchCourses() {
  return api.get<CourseSummary[]>('/courses')
}

export function fetchCourse(courseId: string) {
  return api.get<CourseDetail>(`/courses/${encodeURIComponent(courseId)}`)
}

const examTrackLabels: Record<string, string> = {
  CET4: '四级',
  CET6: '六级',
  IELTS_ACADEMIC: '雅思学术类',
  TOEFL_IBT: '托福 iBT',
  GENERAL: '通用基础',
}

export function examTrackLabel(code: string) {
  return examTrackLabels[code] ?? code
}
