import { createRouter, createWebHistory } from 'vue-router'
import AccountView from '@/features/account/AccountView.vue'
import ForgotPasswordView from '@/features/account/ForgotPasswordView.vue'
import LoginView from '@/features/account/LoginView.vue'
import RegisterView from '@/features/account/RegisterView.vue'
import ResetPasswordView from '@/features/account/ResetPasswordView.vue'
import VerifyEmailView from '@/features/account/VerifyEmailView.vue'
import CourseDetailView from '@/features/catalog/CourseDetailView.vue'
import CoursesView from '@/features/catalog/CoursesView.vue'
import OverviewView from '@/features/progress/OverviewView.vue'
import PracticeResultView from '@/features/practice/PracticeResultView.vue'
import PracticeView from '@/features/practice/PracticeView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/courses' },
    { path: '/courses', name: 'courses', component: CoursesView },
    { path: '/overview', name: 'overview', component: OverviewView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/verify-email', name: 'verify-email', component: VerifyEmailView },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView },
    { path: '/account', name: 'account', component: AccountView },
    {
      path: '/courses/:courseId',
      name: 'course-detail',
      component: CourseDetailView,
      props: true,
    },
    {
      path: '/practice/:sessionId',
      name: 'practice',
      component: PracticeView,
      props: true,
    },
    {
      path: '/practice/:sessionId/result',
      name: 'practice-result',
      component: PracticeResultView,
      props: true,
    },
    { path: '/:pathMatch(.*)*', redirect: '/courses' },
  ],
})
