<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { resetPassword } from '@/features/account/api'
import { useIdentityStore } from '@/features/account/identity'

const route = useRoute()
const identity = useIdentityStore()
const token = typeof route.query.token === 'string' ? route.query.token : ''

const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)
const done = ref(false)

onMounted(() => {
  if (identity.kind === 'UNKNOWN') {
    void identity.loadSession()
  }
})

async function submit() {
  if (submitting.value) {
    return
  }
  error.value = null
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await resetPassword({ token, newPassword: newPassword.value })
    done.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '重置失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">账号</p>
      <h1>重置密码</h1>
    </header>

    <form v-if="!done" class="auth-card" @submit.prevent="submit">
      <label class="field">
        <span>新密码</span>
        <input v-model="newPassword" type="password" autocomplete="new-password" required minlength="8" placeholder="至少 8 位" />
      </label>
      <label class="field">
        <span>确认新密码</span>
        <input v-model="confirmPassword" type="password" autocomplete="new-password" required minlength="8" />
      </label>
      <p v-if="!token" class="notice notice-error">链接缺少重置令牌</p>
      <p v-if="error" class="notice notice-error">{{ error }}</p>
      <button type="submit" class="button" :disabled="submitting || !token">
        {{ submitting ? '正在重置…' : '重置密码' }}
      </button>
    </form>

    <section v-else class="auth-card">
      <p class="auth-note">密码已重置，该账号的旧登录会话已失效。</p>
      <RouterLink class="button" :to="{ name: 'login' }">用新密码登录</RouterLink>
    </section>
  </section>
</template>
