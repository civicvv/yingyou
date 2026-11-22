<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError } from '@/shared/api/client'
import { examTrackLabel } from '@/features/catalog/api'
import { fetchPracticeResult, type PracticeResult } from './api'

const props = defineProps<{ sessionId: string }>()

const router = useRouter()
const result = ref<PracticeResult | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const statusLabels: Record<string, string> = {
  PASSED_INDEPENDENT: '独立完成',
  PASSED_ASSISTED: '辅助完成',
  SKIPPED: '已跳过',
  OPEN: '未完成',
}

onMounted(async () => {
  try {
    result.value = await fetchPracticeResult(props.sessionId)
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 409) {
      await router.replace({ name: 'practice', params: { sessionId: props.sessionId } })
      return
    }
    error.value = cause instanceof Error ? cause.message : '无法读取本节结果'
  } finally {
    loading.value = false
  }
})

function accuracyText(value: number) {
  return `${Math.round(value * 100)}%`
}
</script>

<template>
  <section class="page result-page">
    <RouterLink
      class="back-link"
      :to="result?.courseId ? `/courses/${result.courseId}` : '/courses'"
    >
      返回课程
    </RouterLink>

    <p v-if="loading" class="notice">正在整理本节结果…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <template v-else-if="result">
      <header class="page-heading">
        <p class="eyebrow">{{ result.lessonTitle ?? '整句练习' }}</p>
        <h1>本节结果</h1>
        <p class="lede">共 {{ result.total }} 题，首答正确率 {{ accuracyText(result.firstTryAccuracy) }}。</p>
      </header>

      <div v-if="result.examTrack" class="notice">
        定制批次 · {{ examTrackLabel(result.examTrack) }} · 词汇档 {{ result.vocabBand }}
        · 起始难度 {{ result.baselineDifficulty }}
        <span v-if="result.nextDifficultySuggestion"> · 下批建议 {{ result.nextDifficultySuggestion }}</span>
        <RouterLink class="button button-small" to="/personalized">查看下一批</RouterLink>
      </div>

      <dl class="stat-grid">
        <div class="stat-card">
          <dt>题数</dt>
          <dd>{{ result.total }}</dd>
        </div>
        <div class="stat-card">
          <dt>独立完成</dt>
          <dd>{{ result.independentCount }}</dd>
        </div>
        <div class="stat-card">
          <dt>辅助完成</dt>
          <dd>{{ result.assistedCount }}</dd>
        </div>
        <div class="stat-card">
          <dt>跳过</dt>
          <dd>{{ result.skippedCount }}</dd>
        </div>
        <div class="stat-card">
          <dt>首答正确率</dt>
          <dd>{{ accuracyText(result.firstTryAccuracy) }}</dd>
        </div>
      </dl>

      <ol class="result-list">
        <li v-for="item in result.items" :key="item.position" class="result-row">
          <span class="lesson-index">{{ item.position }}</span>
          <div class="result-main">
            <div class="result-topline">
              <span class="result-status" :data-status="item.status">
                {{ statusLabels[item.status] ?? item.status }}
              </span>
              <span v-if="item.firstTryCorrect" class="result-badge">首答正确</span>
              <span v-if="item.hintUsed" class="result-badge result-badge--muted">用过提示</span>
              <span v-if="item.answerRevealed" class="result-badge result-badge--muted">看过答案</span>
            </div>
            <p class="result-prompt">{{ item.promptZh }}</p>
            <p class="result-answer">{{ item.canonicalEn }}</p>
            <p class="result-meta">作答 {{ item.attemptCount }} 次</p>
          </div>
        </li>
      </ol>
    </template>
  </section>
</template>
