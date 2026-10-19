/**
 * 演示模式配置:角色快切账号映射表
 *
 * 显示开关规则:
 * - dev 构建默认显示,生产构建默认隐藏
 * - localStorage['demo-mode'] = '1' 时生产构建也强制显示
 * - localStorage['demo-mode'] = '0' 时 dev 构建也强制隐藏
 * 注意:退出登录会 localStorage.clear() 清掉该标记,属预期行为
 */
export const DEMO_KEY = 'demo-mode'

export const DEMO_ACCOUNTS = {
  STUDENT: { username: 'zhangsan', password: '123456', label: '学生（张三）', home: '/create-order' },
  WORKER: { username: 'lisi', password: '123456', label: '维修工（李师傅）', home: '/task-list' },
  ADMIN: { username: 'admin', password: '123456', label: '管理员', home: '/dashboard' }
}

export function isDemoEnabled() {
  const flag = localStorage.getItem(DEMO_KEY)
  if (flag === '1') return true
  if (flag === '0') return false
  return import.meta.env.DEV
}
