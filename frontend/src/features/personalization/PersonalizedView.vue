<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { newRequestId } from '@/shared/api/requestId'
import {
  createPersonalizedSession,
  examTracks,
  fetchPreferences,
  updatePreferences,
  type LearningPreferences,
  type PersonalizedBatch,
} from './api'

const identity = useIdentityStore()
const router = useRouter()
const preferences = ref<LearningPreferences | null>(null)
const batch = ref<PersonalizedBatch | null>(null)
const selectedTrack = ref('GENERAL')
const difficultyMode = ref<'AUTO' | 'MANUAL'>('AUTO')
const manualDifficulty = ref('D1')
const loading = ref(true)
const busy = ref(false)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

const difficulties = ['D1', 'D2', 'D3', 'D4']

onMounted(async () => {
  try {
    await identity.ensureGuest()
    await loadPreferences()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取学习偏好'
  } finally {
    loading.value = false
  }
})

async function loadPreferences() {
  preferences.value = await fetchPreferences()
  selectedTrack.value = preferences.value.examTrack
  difficultyMode.value = preferences.value.manualDifficulty ? 'MANUAL' : 'AUTO'
  manualDifficulty.value = preferences.value.manualDifficulty ?? preferences.value.effectiveDifficulty
}

async function save() {
  if (!preferences.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    preferences.value = await updatePreferences({
      requestId: newRequestId(),
      revision: preferences.value.revision,
      examTrack: selectedTrack.value,
      difficultyMode: difficultyMode.value,
      manualDifficulty: difficultyMode.value === 'MANUAL' ? manualDifficulty.value : undefined,
    })
    message.value = '偏好已保存，将用于下一批练习。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败，请重试'
  } finally {
    busy.value = false
  }
}

async function start() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    batch.value = await createPersonalizedSession({
      requestId: newRequestId(),
      examTrack: selectedTrack.value,
    })
    await router.push({ name: 'practice', params: { sessionId: batch.value.session.id } })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法创建定制练习'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page personalized-page">
    <header class="page-heading">
      <p class="eyebrow">定制练习</p>
      <h1>按目标与词汇档出题</h1>
      <p class="lede">每批默认 10 题，创建时固定题目。刷新后可继续同一批次。</p>
    </header>

    <p v-if="loading || !preferences" class="notice">正在读取学习偏好…</p>
    <template v-else>
      <p v-if="message" class="notice">{{ message }}</p>
      <p v-if="error" class="notice notice-error">{{ error }}</p>

      <dl class="stat-grid">
        <div class="stat-card">
          <dt>当前目标</dt>
          <dd class="stat-card-text">{{ examTracks.find((item) => item.code === preferences?.examTrack)?.label ?? preferences?.examTrack }}</dd>
        </div>
        <div class="stat-card">
          <dt>词汇档</dt>
          <dd>{{ preferences.vocabBand }}</dd>
        </div>
        <div class="stat-card">
          <dt>整句难度</dt>
          <dd>{{ preferences.effectiveDifficulty }}</dd>
        </div>
        <div class="stat-card">
          <dt>起始建议</dt>
          <dd>{{ preferences.autoDifficulty }}</dd>
        </div>
      </dl>

      <p v-if="!preferences.lastAssessmentAt" class="notice">
        还没有词汇测评结果，当前按 V1 / D1 起步。
        <RouterLink to="/assessment">先测词汇</RouterLink>
      </p>

      <div class="preference-form">
        <label>
          备考目标
          <select v-model="selectedTrack">
            <option v-for="track in examTracks" :key="track.code" :value="track.code">
              {{ track.label }}
            </option>
          </select>
        </label>
        <label>
          难度模式
          <select v-model="difficultyMode">
            <option value="AUTO">自动调整</option>
            <option value="MANUAL">手动指定</option>
          </select>
        </label>
        <label v-if="difficultyMode === 'MANUAL'">
          指定难度
          <select v-model="manualDifficulty">
            <option v-for="level in difficulties" :key="level" :value="level">{{ level }}</option>
          </select>
        </label>
      </div>

      <div class="page-actions">
        <button type="button" class="button button-ghost" :disabled="busy" @click="save">保存偏好</button>
        <button type="button" class="button" :disabled="busy" @click="start">开始下一批</button>
        <RouterLink class="button button-ghost" to="/assessment">重新测词汇</RouterLink>
      </div>

      <div v-if="batch" class="notice">
        本批 {{ batch.actualCount }} 题：{{ batch.reasons.join('；') }}
        <span v-if="batch.fallbackReason">（{{ batch.fallbackReason }}）</span>
      </div>
    </template>
  </section>
</template>
