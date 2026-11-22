<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { updateNickname } from '@/features/account/api'
import { useIdentityStore } from '@/features/account/identity'

const router = useRouter()
const identity = useIdentityStore()
const { kind, learnerId, nickname, email, loading } = storeToRefs(identity)

const draftNickname = ref('')
const saving = ref(false)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

onMounted(() => {
  if (identity.kind === 'UNKNOWN') {
    void identity.loadSession()
  } else {
    void identity.loadProfile()
  }
})

watch(nickname, (value) => {
  draftNickname.value = value ?? ''
})

async function save() {
  if (saving.value) {
    return
  }
  saving.value = true
  message.value = null
  error.value = null
  try {
    await updateNickname(draftNickname.value.trim())
    await identity.loadProfile()
    message.value = '昵称已保存'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function signOut() {
  await identity.signOut()
  await router.push({ name: 'courses' })
}

function shortId(id: string | null) {
  return id ? id.slice(0, 8) : ''
}
</script>

<template>
  <section class="page auth-page">
    <header class="page-heading">
      <p class="eyebrow">账号</p>
      <h1>我的资料</h1>
    </header>

    <p v-if="loading" class="notice">正在读取身份…</p>

    <section v-else-if="kind === 'ACCOUNT'" class="auth-card">
      <dl class="profile-list">
        <div>
          <dt>邮箱</dt>
          <dd>{{ email }}</dd>
        </div>
        <div>
          <dt>学习者 ID</dt>
          <dd><code>{{ learnerId }}</code></dd>
        </div>
      </dl>

      <label class="field">
        <span>昵称</span>
        <input v-model="draftNickname" type="text" maxlength="40" />
      </label>
      <p v-if="message" class="auth-note">{{ message }}</p>
      <p v-if="error" class="notice notice-error">{{ error }}</p>

      <div class="action-row">
        <button type="button" class="button" :disabled="saving || !draftNickname.trim()" @click="save">
          {{ saving ? '正在保存…' : '保存昵称' }}
        </button>
        <button type="button" class="button button-ghost" @click="signOut">退出登录</button>
        <RouterLink class="button button-quiet" :to="{ name: 'overview' }">学习概览</RouterLink>
      </div>
    </section>

    <section v-else-if="kind === 'GUEST'" class="auth-card">
      <p class="auth-note">
        当前是访客身份 <code>{{ shortId(learnerId) }}</code>。登录或注册后，练习记录会合并到账号。
      </p>
      <div class="action-row">
        <RouterLink class="button" :to="{ name: 'login' }">登录</RouterLink>
        <RouterLink class="button button-ghost" :to="{ name: 'register' }">注册</RouterLink>
      </div>
    </section>

    <section v-else class="auth-card">
      <p class="auth-note">尚未建立学习身份。开始练习会自动保存访客进度，登录后可合并到账号。</p>
      <div class="action-row">
        <RouterLink class="button" :to="{ name: 'login' }">登录</RouterLink>
        <RouterLink class="button button-ghost" :to="{ name: 'register' }">注册</RouterLink>
      </div>
    </section>
  </section>
</template>
