<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { examTrackLabel } from '@/features/catalog/api'
import {
  fetchMyCourses,
  formatDateTime,
  percentText,
  statusLabels,
  type MyCourse,
} from './api'

const identity = useIdentityStore()
const { kind, loading: identityLoading } = storeToRefs(identity)
const route = useRoute()
const migratedNotice = route.query.migrated === '1'

const courses = ref<MyCourse[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const totalCompletedLessons = computed(() =>
  courses.value.reduce((sum, course) => sum + course.completedLessonCount, 0),
)

const mostRecent = computed(() => {
  const values = courses.value
    .map((course) => course.lastPracticedAt)
    .filter((value): value is string => Boolean(value))
    .sort()
  return values.length ? values[values.length - 1] : null
})

onMounted(() => {
  void identity.loadSession()
})

watch(
  kind,
  async (value) => {
    if (value === 'GUEST' || value === 'ACCOUNT') {
      await loadCourses()
    } else if (value === 'ANONYMOUS') {
      courses.value = []
    }
  },
  { immediate: true },
)

async function loadCourses() {
  loading.value = true
  error.value = null
  try {
    courses.value = await fetchMyCourses()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法加载学习概览'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page overview-page">
    <header class="page-heading">
      <p class="eyebrow">学习概览</p>
      <h1>我的课程</h1>
      <p class="lede">按最近练习排序，继续未完成课节或回看上次结果。</p>
    </header>

    <p v-if="identityLoading || loading" class="notice">正在整理学习记录…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <template v-else>
      <p v-if="migratedNotice" class="notice">访客练习记录已合并到账号。</p>
      <dl class="stat-grid">
        <div class="stat-card">
          <dt>练过课程</dt>
          <dd>{{ courses.length }}</dd>
        </div>
        <div class="stat-card">
          <dt>已完成课节</dt>
          <dd>{{ totalCompletedLessons }}</dd>
        </div>
        <div class="stat-card">
          <dt>最近练习</dt>
          <dd class="stat-card-text">{{ formatDateTime(mostRecent) }}</dd>
        </div>
      </dl>

      <p v-if="kind === 'ANONYMOUS'" class="notice">
        还没有学习记录。到<RouterLink to="/courses">课程列表</RouterLink>开始第一节课，进度会保存在当前浏览器。
      </p>
      <p v-else-if="courses.length === 0" class="notice">
        还没有练过的课程。到<RouterLink to="/courses">课程列表</RouterLink>选择一门课开始练习。
      </p>

      <ul v-else class="overview-list">
        <li v-for="course in courses" :key="course.courseId" class="overview-card">
          <div class="overview-card-head">
            <div>
              <span class="track-tag">{{ examTrackLabel(course.examTrack) }}</span>
              <h2>{{ course.title }}</h2>
              <p class="overview-meta">
                已完成 {{ course.completedLessonCount }} / {{ course.lessonCount }} 课节 ·
                {{ percentText(course.completionRate) }} · 最近练习 {{ formatDateTime(course.lastPracticedAt) }}
              </p>
            </div>
            <RouterLink
              class="button button-ghost button-small"
              :to="{ name: 'course-detail', params: { courseId: course.courseId } }"
            >
              课程详情
            </RouterLink>
          </div>

          <div class="progress-track" aria-hidden="true">
            <span
              class="progress-fill"
              :style="{ width: percentText(course.completionRate) }"
            ></span>
          </div>

          <ul class="overview-lessons">
            <li v-for="lesson in course.lessons" :key="lesson.lessonId" class="overview-lesson">
              <div class="overview-lesson-main">
                <span class="result-status" :data-status="lesson.status">
                  {{ statusLabels[lesson.status] }}
                </span>
                <span class="overview-lesson-title">{{ lesson.title }}</span>
                <span class="overview-lesson-meta">{{ lesson.questionCount }} 题</span>
              </div>
              <div class="lesson-actions">
                <RouterLink
                  v-if="lesson.continueSessionId"
                  class="button button-small"
                  :to="{ name: 'practice', params: { sessionId: lesson.continueSessionId } }"
                >
                  继续练习
                </RouterLink>
                <RouterLink
                  v-if="lesson.lastCompletedSessionId"
                  class="button button-ghost button-small"
                  :to="{ name: 'practice-result', params: { sessionId: lesson.lastCompletedSessionId } }"
                >
                  上次结果
                </RouterLink>
              </div>
            </li>
          </ul>

          <div v-if="course.continueSessionId || course.lastCompletedSessionId" class="overview-actions">
            <RouterLink
              v-if="course.continueSessionId"
              class="button"
              :to="{ name: 'practice', params: { sessionId: course.continueSessionId } }"
            >
              继续练习
            </RouterLink>
            <RouterLink
              v-if="course.lastCompletedSessionId"
              class="button button-ghost"
              :to="{ name: 'practice-result', params: { sessionId: course.lastCompletedSessionId } }"
            >
              查看上次结果
            </RouterLink>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>
