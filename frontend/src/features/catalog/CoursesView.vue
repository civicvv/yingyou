<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { fetchMyCourses, percentText, type MyCourse } from '@/features/progress/api'
import type { CourseSummary } from '@/shared/api/types'
import { examTrackLabel, fetchCourses } from './api'

const identity = useIdentityStore()
const { kind } = storeToRefs(identity)

const courses = ref<CourseSummary[]>([])
const myCourses = ref<MyCourse[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const myCoursesById = computed(() => {
  const map = new Map<string, MyCourse>()
  for (const course of myCourses.value) {
    map.set(course.courseId, course)
  }
  return map
})

onMounted(async () => {
  await loadCourses()
})

watch(kind, async (value) => {
  if (value === 'GUEST' || value === 'ACCOUNT') {
    await loadMyCourses()
  }
})

async function loadCourses() {
  loading.value = true
  error.value = null
  try {
    courses.value = await fetchCourses()
    await identity.loadSession()
    if (identity.kind === 'GUEST' || identity.kind === 'ACCOUNT') {
      await loadMyCourses()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法加载课程'
  } finally {
    loading.value = false
  }
}

async function loadMyCourses() {
  try {
    myCourses.value = await fetchMyCourses()
  } catch {
    myCourses.value = []
  }
}
</script>

<template>
  <section class="page">
    <header class="page-heading">
      <p class="eyebrow">我的课程</p>
      <h1>从一门短课开始</h1>
      <p class="lede">选择课程，按课节练习中译英整句表达。</p>
    </header>

    <p v-if="loading" class="notice">正在加载课程…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>
    <p v-else-if="courses.length === 0" class="notice">暂无已发布课程。</p>

    <ul v-else class="course-list">
      <li v-for="course in courses" :key="course.id" class="course-card">
        <div class="course-card-body">
          <span class="track-tag">{{ examTrackLabel(course.examTrack) }}</span>
          <h2>{{ course.title }}</h2>
          <p class="course-summary">{{ course.summary }}</p>
          <p class="course-meta">{{ course.lessonCount }} 个课节</p>
          <p v-if="myCoursesById.get(course.id)" class="course-progress">
            已完成 {{ myCoursesById.get(course.id)!.completedLessonCount }} /
            {{ myCoursesById.get(course.id)!.lessonCount }} 课节 ·
            {{ percentText(myCoursesById.get(course.id)!.completionRate) }}
          </p>
        </div>
        <div class="course-card-actions">
          <RouterLink
            v-if="myCoursesById.get(course.id)?.continueSessionId"
            class="button"
            :to="{
              name: 'practice',
              params: { sessionId: myCoursesById.get(course.id)!.continueSessionId! },
            }"
          >
            继续练习
          </RouterLink>
          <RouterLink
            class="button button-ghost"
            :to="{ name: 'course-detail', params: { courseId: course.id } }"
          >
            查看课节
          </RouterLink>
        </div>
      </li>
    </ul>
  </section>
</template>
