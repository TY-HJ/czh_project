<template>
  <el-container style="height: 100vh">
    <!-- 侧边菜单 -->
    <el-aside width="220px" style="background-color: #263445">
      <div class="logo">🏠 社区报修工单管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#263445"
               text-color="#bfcbd9" active-text-color="#409EFF" style="border-right: none">
        <template v-for="item in menus" :key="item.path">
          <el-menu-item :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="title">{{ $route.meta.title || '首页' }}</div>
        <div class="right">
          <el-badge :value="unread > 0 ? unread : ''" class="msg-badge">
            <el-button text @click="$router.push('/messages')">
              <el-icon><Bell /></el-icon> 消息
            </el-button>
          </el-badge>
          <el-badge is-dot :hidden="!noticeNew" class="msg-badge">
            <el-button text @click="$router.push('/notices')">
              <el-icon><Document /></el-icon> 公告
            </el-button>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ store.realName }}（{{ roleName }}）
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main style="padding: 16px; overflow-y: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getUnreadCount, getNoticePage } from '../api/common'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const unread = ref(0)
const noticeNew = ref(false)
let timer = null
let noticeTimer = null

const roleName = computed(() =>
  ({ ADMIN: '管理员', RESIDENT: '住户', WORKER: '维修工' }[store.role] || store.role))

// 不同角色显示不同菜单(基本功能列清楚,消息/公告提为菜单项,避免藏在顶栏图标里不直观)
const menus = computed(() => {
  if (store.role === 'ADMIN') {
    return [
      { path: '/dashboard', title: '数据统计', icon: 'DataAnalysis' },
      { path: '/order-manage', title: '工单管理', icon: 'Tickets' },
      { path: '/dispatch-rule', title: '派单规则', icon: 'Operation' },
      { path: '/user-manage', title: '用户管理', icon: 'User' },
      { path: '/dict-manage', title: '字典管理', icon: 'Menu' },
      { path: '/notice-manage', title: '公告管理', icon: 'Bell' },
      { path: '/messages', title: '消息中心', icon: 'ChatDotRound' }
    ]
  }
  if (store.role === 'RESIDENT') {
    return [
      { path: '/create-order', title: '提交报修', icon: 'EditPen' },
      { path: '/my-orders', title: '我的报修', icon: 'Tickets' },
      { path: '/messages', title: '消息中心', icon: 'ChatDotRound' },
      { path: '/notices', title: '公告列表', icon: 'Document' }
    ]
  }
  if (store.role === 'WORKER') {
    return [
      { path: '/task-list', title: '维修任务', icon: 'Tools' },
      { path: '/messages', title: '消息中心', icon: 'ChatDotRound' },
      { path: '/notices', title: '公告列表', icon: 'Document' }
    ]
  }
  return []
})

function handleCommand(cmd) {
  if (cmd === 'logout') {
    store.logout()
    router.push('/login')
  }
}

// 每30秒轮询未读消息数
async function refreshUnread() {
  try {
    unread.value = await getUnreadCount()
  } catch (e) { /* 静默 */ }
}

// 公告新内容红点: 对比最新公告时间与本地"最近已看公告"时间
// 用户打开公告列表时, NoticeList 会把最新公告时间写入 localStorage 并广播 notice-seen 事件
async function refreshNotice() {
  if (route.path === '/notices') return
  try {
    const page = await getNoticePage({ current: 1, size: 1 })
    const latest = page.records && page.records[0]
    const seen = localStorage.getItem('noticeSeenAt')
    noticeNew.value = !!latest && (!seen || latest.createTime > seen)
  } catch (e) { /* 静默 */ }
}

function onNoticeSeen() {
  noticeNew.value = false
}

onMounted(() => {
  refreshUnread()
  refreshNotice()
  timer = setInterval(refreshUnread, 30000)
  noticeTimer = setInterval(refreshNotice, 60000)
  window.addEventListener('notice-seen', onNoticeSeen)
})
onUnmounted(() => {
  clearInterval(timer)
  clearInterval(noticeTimer)
  window.removeEventListener('notice-seen', onNoticeSeen)
})
</script>

<style scoped>
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: bold;
  letter-spacing: 1px;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.title { font-size: 16px; font-weight: bold; }
.right { display: flex; align-items: center; gap: 8px; }
.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #333;
  font-size: 14px;
}
.msg-badge { margin-right: 8px; }
</style>
