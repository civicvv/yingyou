<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { resendVerification, verifyEmail } from '@/features/account/api'

const route = useRoute()
const token = typeof route.query.token === 'string' ? route.query.token : ''

const state = ref<'PENDING' | 'SUCCESS' | 'FAILED' | 'MISSING'>('PENDING')
const message = ref('')
const resendEmail = ref('')
const resending = ref(false)
const resendNotice = ref('')

onMounted(async () => {
  if (!token) {
    state.value = 'MISSING'
    message.value = '链接缺少验证令牌'
    return
  }
  try {
    await verifyEmail(token)
    state.value = 'SUCCESS'
    message.value = '邮箱已验证，可以登录了。'
  } catch (cause) {
    state.value = 'FAILED'
    message.value = cause instanceof Error ? cause.message : '验证失败'
  }
})

async function resend() {
  if (resending.value) {
    return
  }
  resending.value = true
  resendNotice.value = ''
  try {
    await resendVerification(resendEmail.value.trim())
    resendNotice.value = '如果该邮箱待验证，新的验证邮件已生成。'
  } catch (cause) {
    resendNotice.value = cause instanceof Error ? cause.message : '发送失败'
  } finally {
    resending.value = false
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">邮箱验证</p>
      <h1>{{ state === 'SUCCESS' ? '验证成功' : '验证邮箱' }}</h1>
    </header>

    <section class="auth-card">
      <p v-if="state === 'SUCCESS'" class="auth-note">{{ message }}</p>
      <p v-else class="notice notice-error">{{ message }}</p>

      <RouterLink v-if="state === 'SUCCESS'" class="button" :to="{ name: 'login' }">
        去登录
      </RouterLink>

      <template v-if="state === 'FAILED' || state === 'MISSING'">
        <div class="auth-divider"></div>
        <label class="field">
          <span>重新发送验证邮件</span>
          <input v-model="resendEmail" type="email" placeholder="you@example.com" />
        </label>
        <button type="button" class="button button-ghost" :disabled="resending || !resendEmail.trim()" @click="resend">
          {{ resending ? '正在发送…' : '重新发送' }}
        </button>
        <p v-if="resendNotice" class="auth-note">{{ resendNotice }}</p>
      </template>
    </section>
  </section>
</template>
