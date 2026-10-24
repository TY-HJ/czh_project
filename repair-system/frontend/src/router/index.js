import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

/**
 * 路由配置:
 * meta.roles 限定可访问角色,路由守卫统一拦截
 */
const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      // 管理员
      { path: 'dashboard', component: () => import('../views/admin/Dashboard.vue'), meta: { roles: ['ADMIN'], title: '数据统计' } },
      { path: 'order-manage', component: () => import('../views/admin/OrderManage.vue'), meta: { roles: ['ADMIN'], title: '工单管理' } },
      { path: 'user-manage', component: () => import('../views/admin/UserManage.vue'), meta: { roles: ['ADMIN'], title: '用户管理' } },
      { path: 'dict-manage', component: () => import('../views/admin/DictManage.vue'), meta: { roles: ['ADMIN'], title: '字典管理' } },
      { path: 'notice-manage', component: () => import('../views/admin/NoticeManage.vue'), meta: { roles: ['ADMIN'], title: '公告管理' } },
      // 住户
      { path: 'create-order', component: () => import('../views/resident/CreateOrder.vue'), meta: { roles: ['RESIDENT'], title: '提交报修' } },
      { path: 'my-orders', component: () => import('../views/resident/MyOrders.vue'), meta: { roles: ['RESIDENT'], title: '我的报修' } },
      // 维修工
      { path: 'task-list', component: () => import('../views/worker/TaskList.vue'), meta: { roles: ['WORKER'], title: '维修任务' } },
      // 公共
      { path: 'messages', component: () => import('../views/common/MessageCenter.vue'), meta: { title: '消息中心' } },
      { path: 'notices', component: () => import('../views/common/NoticeList.vue'), meta: { title: '公告列表' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 各角色首页: 角色不符时跳到自己有权限的首页, 不能跳 '/'
// (根路由会重定向到 ADMIN 的 /dashboard, 跳 '/' 会导致非管理员无限重定向)
const ROLE_HOME = {
  ADMIN: '/dashboard',
  RESIDENT: '/create-order',
  WORKER: '/task-list'
}

function homeFor(role) {
  // 未知角色兜底到无角色限制的公共页, 避免再次陷入重定向循环
  return ROLE_HOME[role] || '/messages'
}

// 全局路由守卫:未登录跳登录页,角色不符回自己的首页
router.beforeEach(to => {
  const store = useUserStore()
  const isAuthPage = to.path === '/login' || to.path === '/register'

  // 未登录(除登录/注册页)
  if (!store.token) {
    return isAuthPage ? true : '/login'
  }
  // 已登录访问登录页 -> 回自己的首页
  if (isAuthPage) {
    return homeFor(store.role)
  }
  // 角色限制: 跳到自己有权限的首页, 而不是 '/'
  if (to.meta.roles && !to.meta.roles.includes(store.role)) {
    return homeFor(store.role)
  }
  return true
})

export default router
