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
      { path: 'my-orders', component: () => import('../views/resident/MyOrders.vue'), meta: { roles: ['RESIDENT'], title: '我的工单' } },
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

// 全局路由守卫:未登录跳登录页,角色不符回首页
router.beforeEach(to => {
  const store = useUserStore()
  // 未登录(除登录/注册页)
  if (!store.token && to.path !== '/login' && to.path !== '/register') {
    return '/login'
  }
  // 已登录访问登录页 -> 回首页
  if (store.token && (to.path === '/login' || to.path === '/register')) {
    return '/'
  }
  // 角色限制
  if (to.meta.roles && !to.meta.roles.includes(store.role)) {
    return '/'
  }
  return true
})

export default router
