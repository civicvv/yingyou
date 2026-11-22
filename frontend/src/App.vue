<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useIdentityStore } from '@/features/account/identity'

const identity = useIdentityStore()
const { kind, learnerId, nickname, email, loading, error } = storeToRefs(identity)

onMounted(() => {
  void identity.loadSession()
})

function shortId(id: string) {
  return id.slice(0, 8)
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink to="/courses" class="brand">
        <span class="brand-mark">Y</span>
        <span class="brand-text">
          <strong>英语整句打字训练</strong>
          <small>看中文，敲英文</small>
        </span>
      </RouterLink>

      <div class="identity">
        <RouterLink class="header-link" to="/overview">学习概览</RouterLink>
        <RouterLink class="header-link" to="/assessment">测词汇</RouterLink>
        <RouterLink class="header-link" to="/personalized">定制练习</RouterLink>
        <span v-if="kind === 'UNKNOWN'" class="identity-text">正在识别身份</span>
        <template v-else-if="kind === 'GUEST'">
          <span class="identity-text">
            访客 · <code>{{ learnerId ? shortId(learnerId) : '' }}</code>
          </span>
          <RouterLink class="button button-ghost button-small" to="/login">登录</RouterLink>
        </template>
        <RouterLink v-else-if="kind === 'ACCOUNT'" class="header-link" to="/account">
          {{ nickname || email || '我的账号' }}
        </RouterLink>
        <template v-else>
          <RouterLink class="button button-ghost button-small" :disabled="loading" to="/login">登录</RouterLink>
          <RouterLink class="button button-small" to="/register">注册</RouterLink>
        </template>
      </div>
    </header>

    <p v-if="error" class="identity-error">{{ error }}</p>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
