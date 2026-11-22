<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { requestPasswordReset } from '@/features/account/api'
import { useIdentityStore } from '@/features/account/identity'

const identity = useIdentityStore()
const email = ref('')
const submitting = ref(false)
const error = ref<string | null>('')
const accepted = ref(false)
const devToken = ref<string | null>(null)

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
  accepted.value = false
  try {
    const result = await requestPasswordReset(email.value.trim())
    accepted.value = result.accepted
    devToken.value = result.devResetToken
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '请求失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">账号</p>
      <h1>找回密码</h1>
      <p class="lede">输入注册邮箱，我们会生成一次性重置链接。</p>
    </header>

    <form class="auth-card" @submit.prevent="submit">
      <label class="field">
        <span>邮箱</span>
        <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com" />
      </label>
      <p v-if="error" class="notice notice-error">{{ error }}</p>
      <button type="submit" class="button" :disabled="submitting">
        {{ submitting ? '正在提交…' : '发送重置链接' }}
      </button>
      <p v-if="accepted" class="auth-note">
        如果该邮箱已注册，重置邮件已生成。请检查收件箱。
      </p>
      <RouterLink
        v-if="devToken"
        class="button button-ghost"
        :to="{ name: 'reset-password', query: { token: devToken } }"
      >
        本地开发：打开重置链接
      </RouterLink>
      <div class="auth-links">
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </form>
  </section>
</template>
