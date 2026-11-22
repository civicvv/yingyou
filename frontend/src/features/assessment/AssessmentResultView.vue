<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  fetchAssessmentResult,
  qualitySignalLabel,
  vocabBandLabel,
  type AssessmentResult,
} from './api'

const props = defineProps<{ sessionId: string }>()
const result = ref<AssessmentResult | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    result.value = await fetchAssessmentResult(props.sessionId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取测评结果'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page result-page">
    <RouterLink class="back-link" to="/overview">返回学习概览</RouterLink>

    <p v-if="loading" class="notice">正在生成测评结果…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>

    <template v-else-if="result">
      <header class="page-heading">
        <p class="eyebrow">测评完成</p>
        <h1>你的起始词汇档：{{ result.vocabBand }}</h1>
        <p class="lede">{{ vocabBandLabel[result.vocabBand] ?? result.vocabBand }} · {{ qualitySignalLabel(result.qualitySignal) }}</p>
      </header>

      <div class="notice">
        首版只展示分档建议。数值估算用于后续校准，暂不展开展示。
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>频段</th>
            <th>答对</th>
            <th>不认识</th>
            <th>校正比例</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stat in result.bandStats" :key="stat.band">
            <td>第 {{ stat.band }} 千词族</td>
            <td>{{ stat.correct }} / {{ stat.total }}</td>
            <td>{{ stat.unknown }}</td>
            <td>{{ Math.round(stat.correctedRatio * 100) }}%</td>
          </tr>
        </tbody>
      </table>

      <div class="page-actions">
        <RouterLink class="button" to="/personalized">开始定制练习</RouterLink>
        <RouterLink class="button button-ghost" to="/courses">直接练习课节</RouterLink>
      </div>
    </template>
  </section>
</template>
