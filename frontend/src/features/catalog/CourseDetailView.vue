<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { createPracticeSession } from '@/features/practice/api'
import { statusLabels } from '@/features/progress/api'
import { newRequestId } from '@/shared/api/requestId'
import type { CourseDetail } from '@/shared/api/types'
import { examTrackLabel, fetchCourse } from './api'

const props = defineProps<{ courseId: string }>()

const router = useRouter()
const identity = useIdentityStore()
const { kind, learnerId, nickname, loading: identityLoading, error: identityError } = storeToRefs(identity)

const course = ref<CourseDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const startingLessonId = ref<string | null>(null)
const startError = ref<string | null>(null)

watch(
  () => props.courseId,
  async (courseId) => {
    loading.value = true
    error.value = null
    course.value = null
    try {
      course.value = await fetchCourse(courseId)
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '无法加载课程'
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function shortId(id: string) {
  return id.slice(0, 8)
}

async function startPractice(lessonId: string) {
  if (startingLessonId.value) {
    return
  }
  startingLessonId.value = lessonId
  startError.value = null
  try {
    await identity.ensureGuest()
    const session = await createPracticeSession(lessonId, newRequestId())
    await router.push({ name: 'practice', params: { sessionId: session.id } })
  } catch (cause) {
    startError.value = cause instanceof Error ? cause.message : '无法开始练习'
  } finally {
    startingLessonId.value = null
  }
}
</script>

<template>
  <section class="page">
    <RouterLink class="back-link" to="/courses">返回课程列表</RouterLink>

    <p v-if="loading" class="notice">正在加载课节…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <template v-else-if="course">
      <header class="course-header">
        <span class="track-tag">{{ examTrackLabel(course.examTrack) }}</span>
        <h1>{{ course.title }}</h1>
        <p class="lede">{{ course.summary }}</p>
      </header>

      <ol class="lesson-list">
        <li v-for="(lesson, index) in course.lessons" :key="lesson.id" class="lesson-row">
          <span class="lesson-index">{{ index + 1 }}</span>
          <div class="lesson-main">
            <h2>{{ lesson.title }}</h2>
            <p>{{ lesson.questionCount }} 道整句题</p>
          </div>
          <div class="lesson-actions">
            <span class="result-status" :data-status="lesson.progressStatus">
              {{ statusLabels[lesson.progressStatus] }}
            </span>
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
            <button
              v-if="!lesson.continueSessionId"
              type="button"
              class="button button-small"
              :class="{ 'button-ghost': Boolean(lesson.lastCompletedSessionId) }"
              :disabled="startingLessonId !== null"
              @click="startPractice(lesson.id)"
            >
              {{
                startingLessonId === lesson.id
                  ? '正在开始…'
                  : lesson.lastCompletedSessionId
                    ? '再练一次'
                    : '开始练习'
              }}
            </button>
          </div>
        </li>
      </ol>

      <p v-if="startError" class="notice notice-error">{{ startError }}</p>

      <section class="identity-panel">
        <div>
          <h2>练习身份</h2>
          <p v-if="kind === 'GUEST'" class="identity-panel-text">
            访客身份已就绪 · <code>{{ learnerId ? shortId(learnerId) : '' }}</code>，同一浏览器刷新后仍会保留。
          </p>
          <p v-else-if="kind === 'ACCOUNT'" class="identity-panel-text">
            账号已登录<template v-if="nickname"> · {{ nickname }}</template>，练习记录会保存到账号。
          </p>
          <p v-else class="identity-panel-text">
            开始练习前，前端会为当前浏览器建立访客身份；登录后可将记录合并到账号。
          </p>
        </div>
        <button
          v-if="kind === 'ANONYMOUS'"
          type="button"
          class="button"
          :disabled="identityLoading"
          @click="identity.createGuest"
        >
          {{ identityLoading ? '正在建立…' : '建立访客身份' }}
        </button>
        <span v-else-if="kind === 'GUEST'" class="status-dot">已建立</span>
      </section>
      <p v-if="identityError" class="notice notice-error">{{ identityError }}</p>
    </template>
  </section>
</template>
