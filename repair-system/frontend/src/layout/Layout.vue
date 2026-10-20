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
          <el-dropdown v-if="demoOn" @command="handleDemoSwitch">
            <el-button text type="warning">
              <el-icon><Switch /></el-icon> 演示切换
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="(acc, role) in DEMO_ACCOUNTS" :key="role"
                                  :command="role" :disabled="role === store.role">
                  {{ acc.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-badge :value="unread > 0 ? unread : ''" class="msg-badge">
            <el-button text @click="$router.push('/messages')">
              <el-icon><Bell /></el-icon> 消息
            </el-button>
          </el-badge>
          <el-button text @click="$router.push('/notices')">
            <el-icon><Document /></el-icon> 公告
          </el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { getUnreadCount } from '../api/common'
import { login } from '../api/auth'
import { DEMO_ACCOUNTS, isDemoEnabled } from '../config/demo'

const store = useUserStore()
const router = useRouter()
const unread = ref(0)
let timer = null

const roleName = computed(() =>
  ({ ADMIN: '管理员', RESIDENT: '住户', WORKER: '维修工' }[store.role] || store.role))

// 不同角色显示不同菜单
const menus = computed(() => {
  if (store.role === 'ADMIN') {
    return [
      { path: '/dashboard', title: '数据统计', icon: 'DataAnalysis' },
      { path: '/order-manage', title: '工单管理', icon: 'Tickets' },
      { path: '/user-manage', title: '用户管理', icon: 'User' },
      { path: '/dict-manage', title: '字典管理', icon: 'Menu' },
      { path: '/notice-manage', title: '公告管理', icon: 'Bell' }
    ]
  }
  if (store.role === 'RESIDENT') {
    return [
      { path: '/create-order', title: '提交报修', icon: 'EditPen' },
      { path: '/my-orders', title: '我的工单', icon: 'Tickets' }
    ]
  }
  if (store.role === 'WORKER') {
    return [{ path: '/task-list', title: '维修任务', icon: 'Tools' }]
  }
  return []
})

function handleCommand(cmd) {
  if (cmd === 'logout') {
    store.logout()
    router.push('/login')
  }
}

// ===== 演示模式:一键切换角色 =====
const demoOn = ref(isDemoEnabled())

async function handleDemoSwitch(role) {
  const acc = DEMO_ACCOUNTS[role]
  if (!acc || role === store.role) return
  try {
    // 先用新账号登录成功后才覆盖当前会话,失败时保持原会话不变(错误提示由 request.js 统一弹出)
    const data = await login({ username: acc.username, password: acc.password })
    store.setLogin(data)
    unread.value = 0
    refreshUnread()
    await router.push(acc.home)
    ElMessage.success(`已切换到${acc.label}视图`)
  } catch (e) { /* 保持原会话 */ }
}

// 每30秒轮询未读消息数
async function refreshUnread() {
  try {
    unread.value = await getUnreadCount()
  } catch (e) { /* 静默 */ }
}

onMounted(() => {
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
})
onUnmounted(() => clearInterval(timer))
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
