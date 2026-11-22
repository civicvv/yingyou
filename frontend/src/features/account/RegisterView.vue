<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { register, TERMS_VERSION } from '@/features/account/api'
import { useIdentityStore } from '@/features/account/identity'

const identity = useIdentityStore()

const email = ref('')
const nickname = ref('')
const password = ref('')
const accepted = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const devToken = ref<string | null>(null)
const registered = ref(false)

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
    const result = await register({
      email: email.value.trim(),
      password: password.value,
      nickname: nickname.value.trim(),
      termsVersion: TERMS_VERSION,
    })
    devToken.value = result.devVerificationToken
    registered.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">账号</p>
      <h1>注册</h1>
      <p class="lede">注册后需要完成邮箱验证才能登录。</p>
    </header>

    <template v-if="registered">
      <section class="auth-card">
        <h2>验证邮件已生成</h2>
        <p class="auth-note">请打开验证链接完成邮箱确认，然后返回登录。</p>
        <RouterLink
          v-if="devToken"
          class="button"
          :to="{ name: 'verify-email', query: { token: devToken } }"
        >
          本地开发：打开验证链接
        </RouterLink>
        <p v-if="devToken" class="dev-note">
          仅本地环境返回验证令牌，生产环境会通过邮件发送。
        </p>
        <RouterLink class="button button-ghost" :to="{ name: 'login' }">去登录</RouterLink>
      </section>
    </template>

    <form v-else class="auth-card" @submit.prevent="submit">
      <label class="field">
        <span>邮箱</span>
        <input v-model="email" type="email" autocomplete="email" required placeholder="you@example.com" />
      </label>
      <label class="field">
        <span>昵称</span>
        <input v-model="nickname" type="text" autocomplete="nickname" required maxlength="40" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="new-password" required minlength="8" placeholder="至少 8 位" />
      </label>
      <label class="checkbox-row">
        <input v-model="accepted" type="checkbox" required />
        <span>我已阅读并同意服务条款（版本 {{ TERMS_VERSION }}）</span>
      </label>

      <p v-if="error" class="notice notice-error">{{ error }}</p>

      <button type="submit" class="button" :disabled="submitting || !accepted">
        {{ submitting ? '正在注册…' : '注册' }}
      </button>

      <div class="auth-links">
        <RouterLink to="/login">已有账号，去登录</RouterLink>
      </div>
    </form>
  </section>
</template>
