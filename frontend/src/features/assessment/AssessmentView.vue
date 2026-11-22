<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { newRequestId } from '@/shared/api/requestId'
import { ApiError } from '@/shared/api/client'
import {
  createAssessment,
  fetchAssessment,
  submitAssessmentAnswer,
  type AssessmentSession,
} from './api'

const STORAGE_KEY = 'yingyou.assessment.session'
const identity = useIdentityStore()
const router = useRouter()
const session = ref<AssessmentSession | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    await identity.ensureGuest()
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      try {
        session.value = await fetchAssessment(saved)
      } catch (cause) {
        if (!(cause instanceof ApiError && cause.status === 404)) throw cause
      }
    }
    if (!session.value) {
      session.value = await createAssessment(newRequestId())
      localStorage.setItem(STORAGE_KEY, session.value.id)
    }
    if (session.value.status === 'COMPLETED') {
      await router.replace({ name: 'assessment-result', params: { sessionId: session.value.id } })
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法开始词汇测评'
  } finally {
    loading.value = false
  }
})

async function answer(optionId?: string) {
  const current = session.value
  if (!current?.currentItem || submitting.value) return
  submitting.value = true
  error.value = null
  try {
    const next = await submitAssessmentAnswer(current.id, {
      requestId: newRequestId(),
      revision: current.revision,
      itemId: current.currentItem.id,
      optionId,
      unknown: !optionId,
    })
    session.value = next
    if (next.status === 'COMPLETED') {
      localStorage.removeItem(STORAGE_KEY)
      await router.push({ name: 'assessment-result', params: { sessionId: next.id } })
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '提交失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page assessment-page">
    <header class="page-heading">
      <p class="eyebrow">词汇测评</p>
      <h1>快速测词汇</h1>
      <p class="lede">一页一题，选择短语境中目标词的意思。不认识可以直接标记，不计分。</p>
    </header>

    <p v-if="loading || !session" class="notice">正在准备试卷…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <template v-else-if="session.currentItem">
      <div class="assessment-progress">
        <span>第 {{ session.position }} / {{ session.total }} 题</span>
        <span>已答 {{ session.answeredCount }} 题</span>
      </div>
      <div class="assessment-card">
        <p class="assessment-word">{{ session.currentItem.word }}</p>
        <p class="assessment-context">{{ session.currentItem.contextEn }}</p>
        <p class="assessment-prompt">{{ session.currentItem.promptZh }}</p>
        <div class="assessment-options">
          <button
            v-for="option in session.currentItem.options"
            :key="option.id"
            type="button"
            class="assessment-option"
            :disabled="submitting"
            @click="answer(option.id)"
          >
            {{ option.label }}
          </button>
        </div>
        <button type="button" class="button button-ghost" :disabled="submitting" @click="answer()">
          不认识／不确定
        </button>
      </div>
    </template>
  </section>
</template>
