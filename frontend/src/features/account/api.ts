import { api } from '@/shared/api/client'

export interface RegisterResult {
  learnerId: string
  verificationRequired: boolean
  devVerificationToken: string | null
}

export interface LoginResult {
  kind: string
  learnerId: string
  nickname: string
  email: string
  guestRecordsMigrated: boolean
  csrfToken: string
}

export interface PasswordResetRequestResult {
  accepted: boolean
  devResetToken: string | null
}

export interface Me {
  kind: 'GUEST' | 'ACCOUNT'
  learnerId: string
  nickname: string | null
  email: string | null
  emailVerified: boolean | null
  createdAt: string | null
}

export function register(body: {
  email: string
  password: string
  nickname: string
  termsVersion: string
}) {
  return api.post<RegisterResult>('/auth/register', body)
}

export function verifyEmail(token: string) {
  return api.post<void>('/auth/verify-email', { token })
}

export function resendVerification(email: string) {
  return api.post<void>('/auth/verification-requests', { email })
}

export function login(body: { email: string; password: string }) {
  return api.post<LoginResult>('/auth/login', body)
}

export function logout() {
  return api.post<void>('/auth/logout')
}

export function requestPasswordReset(email: string) {
  return api.post<PasswordResetRequestResult>('/auth/password-reset-requests', { email })
}

export function resetPassword(body: { token: string; newPassword: string }) {
  return api.post<void>('/auth/password-resets', body)
}

export function fetchMe() {
  return api.get<Me>('/me')
}

export function updateNickname(nickname: string) {
  return api.patch<Me>('/me', { nickname })
}

export const TERMS_VERSION = '2026-11'

export function clearPracticeDrafts() {
  const keys: string[] = []
  for (let index = 0; index < sessionStorage.length; index += 1) {
    const key = sessionStorage.key(index)
    if (key && key.startsWith('yingyou:practice:')) {
      keys.push(key)
    }
  }
  keys.forEach((key) => sessionStorage.removeItem(key))
}
