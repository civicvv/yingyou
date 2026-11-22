import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, setCsrfToken } from '@/shared/api/client'
import { clearPracticeDrafts, fetchMe, logout } from './api'
import type { AuthSession } from '@/shared/api/types'

export const useIdentityStore = defineStore('identity', () => {
  const kind = ref<AuthSession['kind'] | 'UNKNOWN'>('UNKNOWN')
  const learnerId = ref<string | null>(null)
  const nickname = ref<string | null>(null)
  const email = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  function applySession(session: AuthSession) {
    kind.value = session.kind
    learnerId.value = session.learnerId
    setCsrfToken(session.csrfToken)
  }

  async function loadProfile() {
    if (kind.value !== 'GUEST' && kind.value !== 'ACCOUNT') {
      nickname.value = null
      email.value = null
      return
    }
    try {
      const me = await fetchMe()
      nickname.value = me.nickname
      email.value = me.email
    } catch {
      nickname.value = null
      email.value = null
    }
  }

  async function loadSession() {
    loading.value = true
    error.value = null
    try {
      applySession(await api.get<AuthSession>('/auth/session'))
      await loadProfile()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '无法读取会话状态'
    } finally {
      loading.value = false
    }
  }

  async function createGuest() {
    loading.value = true
    error.value = null
    try {
      applySession(await api.post<AuthSession>('/auth/guest'))
      await loadProfile()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '无法建立访客身份'
    } finally {
      loading.value = false
    }
  }

  async function ensureGuest() {
    if (kind.value === 'UNKNOWN') {
      await loadSession()
    }
    if (kind.value === 'GUEST' || kind.value === 'ACCOUNT') {
      return
    }
    if (kind.value === 'ANONYMOUS') {
      await createGuest()
    }
    const currentKind: string = kind.value
    if (currentKind !== 'GUEST' && currentKind !== 'ACCOUNT') {
      throw new Error(error.value ?? '无法建立访客身份')
    }
  }

  async function signOut() {
    loading.value = true
    error.value = null
    try {
      await logout()
      clearPracticeDrafts()
      await loadSession()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '无法退出账号'
    } finally {
      loading.value = false
    }
  }

  async function refreshAfterAuth() {
    clearPracticeDrafts()
    await loadSession()
  }

  return {
    kind,
    learnerId,
    nickname,
    email,
    loading,
    error,
    loadSession,
    loadProfile,
    createGuest,
    ensureGuest,
    signOut,
    refreshAfterAuth,
  }
})
