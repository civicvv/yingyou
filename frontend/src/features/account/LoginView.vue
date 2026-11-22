<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { login } from '@/features/account/api'
import { useIdentityStore } from '@/features/account/identity'

const router = useRouter()
const identity = useIdentityStore()

const email = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)
const migrated = ref(false)

onMounted(() => {
  if (identity.kind === 'UNKNOWN') {
    void identity.loadSession()
  }
})

async function submit() {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    const result = await login({ email: email.value.trim(), password: password.value })
    migrated.value = result.guestRecordsMigrated
    await identity.refreshAfterAuth()
    await router.push({ name: 'overview', query: result.guestRecordsMigrated ? { migrated: '1' } : {} })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">账号</p>
      <h1>登录</h1>
      <p class="lede">登录后，当前浏览器里的访客练习记录会合并到账号。</p>
    </header>

    <form class="auth-card" @submit.prevent="submit">
      <label class="field">
        <span>邮箱</span>
        <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required placeholder="至少 8 位" />
      </label>

      <p v-if="error" class="notice notice-error">{{ error }}</p>

      <button type="submit" class="button" :disabled="submitting">
        {{ submitting ? '正在登录…' : '登录' }}
      </button>

      <div class="auth-links">
        <RouterLink to="/register">注册新账号</RouterLink>
        <RouterLink to="/forgot-password">忘记密码</RouterLink>
      </div>
    </form>

    <p v-if="migrated" class="notice">访客记录已合并到账号。</p>
  </section>
</template>
