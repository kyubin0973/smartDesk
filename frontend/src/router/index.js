import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('../views/ForgotPasswordView.vue'),
    meta: { public: true },
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('../views/ResetPasswordView.vue'),
    meta: { public: true },
  },

  // SI 담당자
  {
    path: '/',
    name: 'dashboard',
    component: () => import('../views/SiDashboardView.vue'),
    meta: { si: true },
  },
  {
    path: '/clients',
    name: 'clients',
    component: () => import('../views/ClientListView.vue'),
    meta: { si: true },
  },
  {
    path: '/clients/:clientId/contract',
    name: 'contract',
    component: () => import('../views/ContractDetailView.vue'),
    meta: { si: true },
  },
  {
    path: '/tickets/:ticketId',
    name: 'ticket-detail',
    component: () => import('../views/TicketDetailView.vue'),
    meta: { auth: true },
  },
  {
    path: '/docs',
    name: 'docs',
    component: () => import('../views/DocListView.vue'),
    meta: { auth: true },
  },
  {
    path: '/docs/new',
    name: 'doc-new',
    component: () => import('../views/DocEditView.vue'),
    meta: { si: true },
  },
  {
    path: '/docs/:documentId/edit',
    name: 'doc-edit',
    component: () => import('../views/DocEditView.vue'),
    meta: { si: true },
  },
  {
    path: '/docs/:documentId',
    name: 'doc-detail',
    component: () => import('../views/DocDetailView.vue'),
    meta: { auth: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { si: true },
  },
  {
    path: '/audit',
    name: 'audit',
    component: () => import('../views/AuditLogView.vue'),
    meta: { si: true, manager: true },
  },
  {
    path: '/reports/sla',
    name: 'sla-report',
    component: () => import('../views/SlaReportView.vue'),
    meta: { si: true, manager: true },
  },
  {
    path: '/analytics',
    name: 'analytics',
    component: () => import('../views/AnalyticsView.vue'),
    meta: { si: true, manager: true },
  },

  // 고객사 담당자
  {
    path: '/portal',
    name: 'portal',
    component: () => import('../views/ClientPortalView.vue'),
    meta: { client: true },
  },
  {
    path: '/portal/tickets/new',
    name: 'ticket-new',
    component: () => import('../views/TicketNewView.vue'),
    meta: { client: true },
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isAuthed) return { name: 'login' }
  if (to.meta.client && !auth.isClientUser) return { name: 'dashboard' }
  if (to.meta.si && auth.isClientUser) return { name: 'portal' }
  if (to.meta.manager && !auth.isManager) return { name: 'dashboard' }
  return true
})

export default router
