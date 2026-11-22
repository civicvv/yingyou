<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'
import { ApiError } from '@/shared/api/client'
import { newRequestId } from '@/shared/api/requestId'
import {
  checkPrefix,
  fetchPracticeSession,
  sendCommand,
  type PracticeAction,
  type PracticeSessionView,
} from './api'

const props = defineProps<{ sessionId: string }>()

const router = useRouter()
const identity = useIdentityStore()
const { kind } = storeToRefs(identity)

const session = ref<PracticeSessionView | null>(null)
const answer = ref('')
const loading = ref(true)
const submitting = ref(false)
const submittingAction = ref<PracticeAction | null>(null)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const isComposing = ref(false)
const prefixState = ref<{ matchedCount: number; firstMismatchIndex: number | null } | null>(null)
const mismatchDismissed = ref(false)

const answerInput = ref<HTMLInputElement | null>(null)
const nextButton = ref<HTMLButtonElement | null>(null)

let prefixTimer: number | undefined
let prefixSequence = 0
let pendingRetry: { action: PracticeAction; text: string; requestId: string } | null = null

const currentItem = computed(() => session.value?.currentItem ?? null)
const feedback = computed(() => session.value?.feedback ?? null)
const terminated = computed(() => currentItem.value !== null && currentItem.value.status !== 'OPEN')
const completed = computed(() => session.value?.status === 'COMPLETED')
const words = computed(() => (answer.value.trim() ? answer.value.trim().split(/\s+/) : []))
const progressPercent = computed(() => {
  if (!session.value || session.value.total === 0) {
    return 0
  }
  return Math.min(100, (session.value.position / session.value.total) * 100)
})

const activeMismatch = computed(() => {
  const viewFeedback = feedback.value
  if (viewFeedback && viewFeedback.matchedCount !== null && !mismatchDismissed.value) {
    return {
      matched: viewFeedback.matchedCount,
      index: viewFeedback.firstMismatchIndex,
      type: viewFeedback.differenceType,
    }
  }
  if (prefixState.value) {
    return {
      matched: prefixState.value.matchedCount,
      index: prefixState.value.firstMismatchIndex,
      type: null,
    }
  }
  return { matched: 0, index: null, type: null }
})

const detailNote = computed(() => {
  const viewFeedback = feedback.value
  if (!viewFeedback || terminated.value || mismatchDismissed.value) {
    return null
  }
  if (viewFeedback.matchedCount === null) {
    return null
  }
  return mismatchLabel(viewFeedback.firstMismatchIndex, viewFeedback.differenceType)
})

const statusMessage = computed(() => {
  if (!session.value) {
    return ''
  }
  if (terminated.value) {
    if (currentItem.value?.status === 'PASSED_INDEPENDENT') {
      return '回答正确，本题独立完成。'
    }
    if (currentItem.value?.status === 'PASSED_ASSISTED') {
      return '回答正确，本题记为辅助完成。'
    }
    if (currentItem.value?.status === 'SKIPPED') {
      return '已跳过本题。'
    }
  }
  if (detailNote.value) {
    return `和目标表达还不一致，${detailNote.value}`
  }
  if (feedback.value?.answer && !terminated.value) {
    return '已公布参考答案，可以继续修改作答。'
  }
  if (feedback.value?.hint) {
    return `已使用提示，首词是 ${feedback.value.hint}，本题答对将记为辅助完成。`
  }
  return ''
})

const primaryActionLabel = computed(() => {
  if (session.value && session.value.position >= session.value.total) {
    return '查看本节结果'
  }
  return '下一题'
})

onMounted(async () => {
  try {
    await identity.ensureGuest()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法建立学习身份'
    loading.value = false
    return
  }
  await loadInitial()
})

onBeforeUnmount(() => {
  window.clearTimeout(prefixTimer)
})

watch(answer, (value) => {
  const item = currentItem.value
  if (!item || item.status !== 'OPEN') {
    return
  }
  sessionStorage.setItem(draftKey(item.id), value)
})

async function loadInitial() {
  loading.value = true
  error.value = null
  try {
    const view = await fetchPracticeSession(props.sessionId)
    if (view.status === 'COMPLETED') {
      await router.replace({ name: 'practice-result', params: { sessionId: props.sessionId } })
      return
    }
    applyView(view)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取练习会话'
  } finally {
    loading.value = false
  }
}

function applyView(view: PracticeSessionView) {
  const previousItemId = session.value?.currentItem?.id ?? null
  session.value = view

  if (view.status === 'COMPLETED') {
    return
  }

  const item = view.currentItem
  if (!item) {
    return
  }

  if (item.id !== previousItemId) {
    if (previousItemId) {
      sessionStorage.removeItem(draftKey(previousItemId))
    }
    answer.value = sessionStorage.getItem(draftKey(item.id)) ?? ''
    prefixState.value = null
    mismatchDismissed.value = false
    pendingRetry = null
    nextTick(() => {
      if (item.status === 'OPEN') {
        answerInput.value?.focus()
      } else {
        nextButton.value?.focus()
      }
    })
    return
  }

  if (item.status !== 'OPEN') {
    nextTick(() => nextButton.value?.focus())
  }
}

function draftKey(itemId: string) {
  return `yingyou:practice:${props.sessionId}:${itemId}`
}

function mismatchLabel(index: number | null, type: string | null) {
  if (index === null) {
    return ''
  }
  const position = index + 1
  if (type === 'MISSING') {
    return `第 ${position} 个词附近缺少内容`
  }
  if (type === 'EXTRA') {
    return `第 ${position} 个词可能多余`
  }
  return `第 ${position} 个词可能拼错`
}

function wordClass(index: number) {
  if (index < activeMismatch.value.matched) {
    return 'word word--matched'
  }
  if (index === activeMismatch.value.index) {
    return 'word word--fix'
  }
  return 'word word--pending'
}

function completedPrefix(text: string) {
  const withoutTrailingWord = text.replace(/\S+$/, '')
  return withoutTrailingWord.trim() ? withoutTrailingWord : ''
}

function onInput() {
  if (feedback.value?.matchedCount !== null) {
    mismatchDismissed.value = true
  }
  schedulePrefixCheck()
}

function onCompositionStart() {
  isComposing.value = true
  window.clearTimeout(prefixTimer)
}

function onCompositionEnd() {
  isComposing.value = false
  schedulePrefixCheck()
}

function onEnter(event: KeyboardEvent) {
  if (event.repeat || event.isComposing || isComposing.value) {
    return
  }
  if (terminated.value || submitting.value) {
    return
  }
  void submit()
}

function schedulePrefixCheck() {
  window.clearTimeout(prefixTimer)
  if (isComposing.value || !session.value || !currentItem.value || terminated.value) {
    return
  }
  const prefix = completedPrefix(answer.value)
  if (!prefix) {
    prefixState.value = null
    return
  }

  const itemId = currentItem.value.id
  const revision = session.value.revision
  prefixTimer = window.setTimeout(async () => {
    const sequence = ++prefixSequence
    try {
      const result = await checkPrefix(props.sessionId, { revision, itemId, text: prefix })
      if (sequence !== prefixSequence) {
        return
      }
      if (!session.value || session.value.revision !== revision || currentItem.value?.id !== itemId) {
        return
      }
      if (completedPrefix(answer.value) !== prefix) {
        return
      }
      prefixState.value = {
        matchedCount: result.matchedCount,
        firstMismatchIndex: result.firstMismatchIndex,
      }
    } catch {
      // 预检失败不阻塞整句提交，保留用户输入。
    }
  }, 200)
}

async function submit() {
  if (!answer.value.trim()) {
    notice.value = '请先输入英文句子'
    answerInput.value?.focus()
    return
  }
  await runCommand('SUBMIT', answer.value)
}

async function askHint() {
  await runCommand('HINT')
}

async function revealAnswer() {
  await runCommand('REVEAL')
}

async function skipItem() {
  await runCommand('SKIP')
}

async function goNext() {
  await runCommand('NEXT')
}

async function runCommand(action: PracticeAction, text?: string) {
  const view = session.value
  const item = currentItem.value
  if (!view || !item || submitting.value) {
    return
  }

  submitting.value = true
  submittingAction.value = action
  notice.value = null
  error.value = null
  window.clearTimeout(prefixTimer)

  try {
    const requestId = requestIdFor(action, text)
    const result = await sendCommand(props.sessionId, {
      requestId,
      revision: view.revision,
      itemId: item.id,
      action,
      text,
    })
    pendingRetry = null

    if (action === 'SUBMIT' && result.session.currentItem?.status === 'OPEN') {
      mismatchDismissed.value = false
    }

    applyView(result.session)

    if (result.session.status === 'COMPLETED') {
      await router.push({ name: 'practice-result', params: { sessionId: props.sessionId } })
    }
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 409) {
      pendingRetry = null
      await reloadLatest('练习进度已更新，已为你刷新到最新状态。')
    } else if (cause instanceof ApiError) {
      error.value = cause.message
    } else {
      notice.value = '网络异常，输入已保留，可再次点击按钮重试提交。'
    }
  } finally {
    submitting.value = false
    submittingAction.value = null
  }
}

function requestIdFor(action: PracticeAction, text?: string) {
  const normalizedText = text ?? ''
  if (pendingRetry && pendingRetry.action === action && pendingRetry.text === normalizedText) {
    return pendingRetry.requestId
  }
  const requestId = newRequestId()
  pendingRetry = { action, text: normalizedText, requestId }
  return requestId
}

async function reloadLatest(message: string) {
  try {
    const view = await fetchPracticeSession(props.sessionId)
    if (view.status === 'COMPLETED') {
      await router.replace({ name: 'practice-result', params: { sessionId: props.sessionId } })
      return
    }
    applyView(view)
    notice.value = message
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取最新练习进度'
  }
}
</script>

<template>
  <section class="page practice-page">
    <RouterLink
      class="back-link"
      :to="session?.courseId ? `/courses/${session.courseId}` : '/courses'"
    >
      返回课程
    </RouterLink>

    <p v-if="loading" class="notice">正在读取练习进度…</p>
    <p v-else-if="error" class="notice notice-error">{{ error }}</p>
    <p v-else-if="completed" class="notice">本节已完成。</p>

    <template v-else-if="session && currentItem">
      <header class="practice-header">
        <div>
          <p class="eyebrow">{{ session.lessonTitle ?? '整句练习' }}</p>
          <h1>第 {{ session.position }} / {{ session.total }} 题</h1>
        </div>
        <span v-if="kind === 'GUEST'" class="identity-text">访客练习</span>
      </header>
      <div class="progress-track" aria-hidden="true">
        <span class="progress-fill" :style="{ width: progressPercent + '%' }"></span>
      </div>

      <article class="prompt-card">
        <p class="prompt-zh">{{ currentItem.promptZh }}</p>
        <p v-if="currentItem.contextZh" class="prompt-context">{{ currentItem.contextZh }}</p>
      </article>

      <div class="typing-area">
        <label class="typing-label" for="answer-input">写出对应的英文句子</label>
        <input
          id="answer-input"
          ref="answerInput"
          v-model="answer"
          class="typing-input"
          type="text"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
          spellcheck="false"
          enterkeyhint="send"
          placeholder="在这里输入英文句子"
          :disabled="terminated"
          @input="onInput"
          @keydown.enter.prevent="onEnter"
          @compositionstart="onCompositionStart"
          @compositionend="onCompositionEnd"
        />

        <div v-if="!terminated && words.length" class="word-row" aria-hidden="true">
          <span v-for="(word, index) in words" :key="index" :class="wordClass(index)">
            {{ word }}
          </span>
        </div>

        <p class="typing-hint">按 Enter 或点击「检查」提交整句。</p>

        <p class="feedback-note" :class="{ 'feedback-note--error': Boolean(detailNote) }" aria-live="polite">
          {{ statusMessage }}
        </p>
        <p v-if="notice" class="notice notice-inline">{{ notice }}</p>
      </div>

      <div v-if="!terminated" class="action-row">
        <button
          type="button"
          class="button"
          :disabled="submitting || !answer.trim()"
          @click="submit"
        >
          {{ submitting && submittingAction === 'SUBMIT' ? '正在检查…' : '检查' }}
        </button>
        <button
          type="button"
          class="button button-ghost"
          :disabled="submitting || currentItem.hintUsed"
          @click="askHint"
        >
          {{ currentItem.hintUsed ? '已用提示' : '给个提示' }}
        </button>
        <button
          type="button"
          class="button button-ghost"
          :disabled="submitting || currentItem.answerRevealed"
          @click="revealAnswer"
        >
          {{ currentItem.answerRevealed ? '已看答案' : '看参考答案' }}
        </button>
        <button
          type="button"
          class="button button-quiet"
          :disabled="submitting"
          @click="skipItem"
        >
          跳过
        </button>
      </div>

      <section v-if="feedback?.hint && !terminated" class="hint-box">
        <span class="hint-label">首词提示</span>
        <strong>{{ feedback.hint }}</strong>
      </section>

      <section v-if="feedback?.answer" class="explanation-card">
        <p class="explanation-label">
          {{ terminated ? '参考答案与讲解' : '参考答案' }}
        </p>
        <p class="answer-en">{{ feedback.answer }}</p>
        <p v-if="feedback.explanationZh" class="explanation-zh">{{ feedback.explanationZh }}</p>

        <ul v-if="feedback.explanationSegments?.length" class="segment-list">
          <li v-for="(segment, index) in feedback.explanationSegments" :key="index" class="segment">
            <span class="segment-en">{{ segment.text }}</span>
            <span class="segment-gloss">{{ segment.gloss }}</span>
          </li>
        </ul>

        <div v-if="feedback.grammarTags?.length" class="tag-row">
          <span v-for="tag in feedback.grammarTags" :key="tag" class="tag">{{ tag }}</span>
        </div>

        <p v-if="feedback.nextPreview" class="next-preview">
          下一题：{{ feedback.nextPreview.promptZh }}
        </p>

        <p v-if="terminated" class="session-progress">
          本节已处理 {{ session.position }} / {{ session.total }} 题
        </p>

        <button
          v-if="terminated"
          ref="nextButton"
          type="button"
          class="button"
          :disabled="submitting"
          @click="goNext"
        >
          {{ submitting && submittingAction === 'NEXT' ? '正在进入…' : primaryActionLabel }}
        </button>
      </section>

      <section v-else-if="terminated" class="explanation-card">
        <p class="explanation-label">本题已结束</p>
        <button
          ref="nextButton"
          type="button"
          class="button"
          :disabled="submitting"
          @click="goNext"
        >
          {{ submitting && submittingAction === 'NEXT' ? '正在进入…' : primaryActionLabel }}
        </button>
      </section>
    </template>
  </section>
</template>
