<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">🏠 社区报修工单管理系统</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password
                    :prefix-icon="Lock" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">登 录</el-button>
        <div class="tips">
          还没有账号？<router-link to="/register">住户注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 登录成功后按角色跳转对应首页
const HOME_BY_ROLE = { ADMIN: '/dashboard', RESIDENT: '/create-order', WORKER: '/task-list' }

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await login(form)
    store.setLogin(data)
    ElMessage.success('登录成功')
    router.push(HOME_BY_ROLE[data.role] || '/my-orders')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card { width: 400px; padding: 12px 8px; }
.title { text-align: center; margin-bottom: 24px; color: #303133; }
.tips { text-align: center; margin-top: 14px; color: #999; font-size: 13px; }
</style>
