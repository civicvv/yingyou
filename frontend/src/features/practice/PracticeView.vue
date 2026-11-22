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
const slotValues = ref<string[]>([])
const activeSlot = ref(0)
const slotRefs = ref<Record<number, HTMLInputElement | null>>({})
const loading = ref(true)
const submitting = ref(false)
const submittingAction = ref<PracticeAction | null>(null)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const isComposing = ref(false)
const prefixState = ref<{ matchedCount: number; firstMismatchIndex: number | null } | null>(null)
const mismatchDismissed = ref(false)

const nextButton = ref<HTMLButtonElement | null>(null)

let prefixTimer: number | undefined
let prefixSequence = 0
let pendingRetry: { action: PracticeAction; text: string; wordIndex: number | null; requestId: string } | null =
  null

const currentItem = computed(() => session.value?.currentItem ?? null)
const feedback = computed(() => session.value?.feedback ?? null)
const terminated = computed(() => currentItem.value !== null && currentItem.value.status !== 'OPEN')
const completed = computed(() => session.value?.status === 'COMPLETED')
const answer = computed(() => slotValues.value.map((value) => value.trim()).join(' ').trim())
const hasInput = computed(() => answer.value.length > 0)
const wordLengths = computed(() => currentItem.value?.wordLengths ?? [])
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
    return `已使用提示：${feedback.value.hint}。本题答对将记为辅助完成。`
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

watch(
  slotValues,
  (values) => {
    const item = currentItem.value
    if (!item || item.status !== 'OPEN') {
      return
    }
    sessionStorage.setItem(draftKey(item.id), JSON.stringify(values))
  },
  { deep: true },
)

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
    restoreDraft(item.id, item.wordLengths?.length ?? 0)
    prefixState.value = null
    mismatchDismissed.value = false
    pendingRetry = null
    nextTick(() => {
      if (item.status === 'OPEN') {
        focusSlot(activeSlot.value)
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

function slotState(index: number) {
  const mismatch = activeMismatch.value
  if (index === mismatch.index) {
    return 'fix'
  }
  if (index === activeSlot.value) {
    return 'active'
  }
  if (mismatch.index !== null && index > mismatch.index) {
    return 'pending'
  }
  if (index < mismatch.matched) {
    return 'matched'
  }
  if (slotValues.value[index]?.trim()) {
    return 'matched'
  }
  return 'pending'
}

function slotWidth(index: number) {
  const length = wordLengths.value[index] ?? 6
  return `${Math.min(Math.max(length + 1, 3), 26)}ch`
}

function setSlotRef(index: number, element: unknown) {
  slotRefs.value[index] = (element as HTMLInputElement | null) ?? null
}

function focusSlot(index: number) {
  const target = Math.max(0, Math.min(index, Math.max(0, wordLengths.value.length - 1)))
  activeSlot.value = target
  nextTick(() => {
    const element = slotRefs.value[target]
    element?.focus()
    element?.setSelectionRange(element.value.length, element.value.length)
  })
}

function setActiveSlot(index: number) {
  activeSlot.value = index
  schedulePrefixCheck()
}

function firstEmptySlot() {
  const index = slotValues.value.findIndex((value) => !value.trim())
  return index < 0 ? Math.max(0, slotValues.value.length - 1) : index
}

function restoreDraft(itemId: string, count: number) {
  const raw = sessionStorage.getItem(draftKey(itemId))
  let words: string[] = []
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        words = parsed.map((value) => String(value ?? ''))
      } else {
        words = String(parsed).split(/\s+/)
      }
    } catch {
      words = raw.split(/\s+/)
    }
  }
  words = words.slice(0, count)
  while (words.length < count) {
    words.push('')
  }
  slotValues.value = words
  activeSlot.value = firstEmptySlot()
}

function distributeWords(startIndex: number, text: string) {
  const words = text.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    slotValues.value[startIndex] = ''
    focusSlot(startIndex)
    return
  }
  let cursor = startIndex
  for (const word of words) {
    if (cursor >= wordLengths.value.length) {
      break
    }
    slotValues.value[cursor] = word
    cursor += 1
  }
  mismatchDismissed.value = true
  focusSlot(Math.min(cursor, Math.max(0, wordLengths.value.length - 1)))
  schedulePrefixCheck()
}

function onSlotInput(index: number, event: Event) {
  const input = event.target as HTMLInputElement
  const raw = input.value
  activeSlot.value = index
  if (/\s/.test(raw)) {
    distributeWords(index, raw)
    return
  }
  mismatchDismissed.value = true
  schedulePrefixCheck()
}

function onSpace(index: number) {
  if (!slotValues.value[index]?.trim()) {
    notice.value = '请先输入这个单词'
    return
  }
  notice.value = null
  mismatchDismissed.value = true
  focusSlot(index + 1)
  schedulePrefixCheck()
}

function onBackspace(index: number, event: KeyboardEvent) {
  if (!slotValues.value[index]?.trim() && index > 0) {
    event.preventDefault()
    focusSlot(index - 1)
  }
}

function onPaste(index: number, event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text') ?? ''
  if (!text) {
    return
  }
  event.preventDefault()
  distributeWords(index, text)
}

function onCompositionStart() {
  isComposing.value = true
  window.clearTimeout(prefixTimer)
}

function onCompositionEnd(index: number) {
  isComposing.value = false
  activeSlot.value = index
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

function completedPrefix() {
  const completed = slotValues.value
    .slice(0, activeSlot.value)
    .map((value) => value.trim())
    .join(' ')
    .trim()
  return completed
}

function schedulePrefixCheck() {
  window.clearTimeout(prefixTimer)
  if (isComposing.value || !session.value || !currentItem.value || terminated.value) {
    return
  }
  const prefix = completedPrefix()
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
      if (completedPrefix() !== prefix) {
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
    focusSlot(firstEmptySlot())
    return
  }
  await runCommand('SUBMIT', answer.value)
}

async function askHint() {
  await runCommand('HINT', undefined, activeSlot.value)
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

async function runCommand(action: PracticeAction, text?: string, wordIndex?: number) {
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
    const requestId = requestIdFor(action, text, wordIndex)
    const result = await sendCommand(props.sessionId, {
      requestId,
      revision: view.revision,
      itemId: item.id,
      action,
      text,
      wordIndex: action === 'HINT' ? wordIndex : undefined,
    })
    pendingRetry = null

    if (action === 'SUBMIT' && result.session.currentItem?.status === 'OPEN') {
      mismatchDismissed.value = false
    }

    applyView(result.session)

    if (result.session.status === 'COMPLETED') {
      await router.push({ name: 'practice-result', params: { sessionId: props.sessionId } })
      return
    }

    if (result.session.currentItem?.status === 'OPEN') {
      if (action === 'HINT' || action === 'REVEAL') {
        focusSlot(activeSlot.value)
      } else if (action === 'SUBMIT') {
        const mismatch = result.session.feedback?.firstMismatchIndex
        focusSlot(mismatch ?? activeSlot.value)
      }
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

function requestIdFor(action: PracticeAction, text?: string, wordIndex?: number) {
  const normalizedText = text ?? ''
  const normalizedIndex = action === 'HINT' && wordIndex !== undefined ? wordIndex : null
  if (
    pendingRetry &&
    pendingRetry.action === action &&
    pendingRetry.text === normalizedText &&
    pendingRetry.wordIndex === normalizedIndex
  ) {
    return pendingRetry.requestId
  }
  const requestId = newRequestId()
  pendingRetry = { action, text: normalizedText, wordIndex: normalizedIndex, requestId }
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
        <div class="typing-header">
          <label class="typing-label">写出对应的英文句子</label>
          <span v-if="wordLengths.length" class="slot-progress">
            第 {{ activeSlot + 1 }} / {{ wordLengths.length }} 词
          </span>
        </div>

        <div class="slot-row">
          <input
            v-for="(length, index) in wordLengths"
            :key="index"
            :ref="(element) => setSlotRef(index, element)"
            v-model="slotValues[index]"
            class="slot-input"
            :class="`slot--${slotState(index)}`"
            :style="{ width: slotWidth(index) }"
            type="text"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
            spellcheck="false"
            enterkeyhint="next"
            :disabled="terminated"
            :aria-label="`第 ${index + 1} 个单词`"
            :aria-current="index === activeSlot ? 'true' : undefined"
            @focus="setActiveSlot(index)"
            @input="onSlotInput(index, $event)"
            @keydown.space.prevent="onSpace(index)"
            @keydown.backspace="onBackspace(index, $event)"
            @keydown.enter.prevent="onEnter"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd(index)"
            @paste.prevent="onPaste(index, $event)"
          />
        </div>

        <p class="typing-hint">输入一个单词后按空格跳到下一词；按 Enter 或点击「检查」提交整句。</p>

        <p class="feedback-note" :class="{ 'feedback-note--error': Boolean(detailNote) }" aria-live="polite">
          {{ statusMessage }}
        </p>
        <p v-if="notice" class="notice notice-inline">{{ notice }}</p>
      </div>

      <div v-if="!terminated" class="action-row">
        <button
          type="button"
          class="button"
          :disabled="submitting || !hasInput"
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
        <span class="hint-label">提示词</span>
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
