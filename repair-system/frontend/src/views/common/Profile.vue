<template>
  <div class="profile-page">
    <!-- 顶部身份卡 -->
    <el-card class="profile-card">
      <div class="profile-head">
        <div class="avatar">{{ avatarChar }}</div>
        <div class="head-info">
          <div class="name">{{ info.realName || '-' }}</div>
          <div class="sub">{{ roleName }} · {{ info.username }}</div>
          <div class="sub">注册时间：{{ info.createTime || '-' }}</div>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <!-- 基本资料 -->
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><b>基本资料</b></template>
          <el-form ref="profileRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="用户名">
              <el-input :model-value="info.username" disabled />
            </el-form-item>
            <el-form-item label="角色">
              <el-input :model-value="roleName" disabled />
            </el-form-item>
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" maxlength="20" show-word-limit placeholder="请输入真实姓名" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" maxlength="11" placeholder="选填，便于维修工联系" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
              <el-button @click="resetProfile">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 修改密码 -->
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header><b>修改密码</b></template>
          <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="96px">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwd.newPassword" type="password" show-password placeholder="6~20 位" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirm">
              <el-input v-model="pwd.confirm" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changing" @click="submitPassword">修改密码</el-button>
              <span class="tip">修改成功后请用新密码重新登录</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile, changePassword } from '../../api/profile'
import { useUserStore } from '../../stores/user'

const store = useUserStore()
const info = reactive({ username: '', realName: '', phone: '', role: '', createTime: '' })
const form = reactive({ realName: '', phone: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const profileRef = ref(null)
const pwdRef = ref(null)
const saving = ref(false)
const changing = ref(false)

const roleName = computed(() =>
  ({ ADMIN: '管理员', RESIDENT: '住户', WORKER: '维修工' }[info.role] || info.role || '-'))

const avatarChar = computed(() => {
  const name = info.realName || info.username || '?'
  return name.slice(0, 1).toUpperCase()
})

const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{
    // 选填: 空值放行, 填了才校验格式
    validator: (rule, value, callback) =>
      (!value || /^1[3-9]\d{9}$/.test(value)) ? callback() : callback(new Error('手机号格式不正确')),
    trigger: 'blur'
  }]
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6~20 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) =>
        value === pwd.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function load() {
  const data = await getProfile()
  Object.assign(info, {
    username: data.username,
    realName: data.realName,
    phone: data.phone || '',
    role: data.role,
    createTime: data.createTime
  })
  resetProfile()
}

function resetProfile() {
  form.realName = info.realName || ''
  form.phone = info.phone || ''
  if (profileRef.value) profileRef.value.clearValidate()
}

async function saveProfile() {
  const valid = await profileRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await updateProfile({ realName: form.realName.trim(), phone: form.phone.trim() })
    info.realName = form.realName.trim()
    info.phone = form.phone.trim()
    // 顶栏显示的是 store.realName, 同步刷新避免"改了名字但右上角没变"
    store.realName = info.realName
    localStorage.setItem('realName', info.realName)
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

async function submitPassword() {
  const valid = await pwdRef.value.validate().catch(() => false)
  if (!valid) return
  changing.value = true
  try {
    await changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    pwdRef.value.resetFields()
  } finally {
    changing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.profile-page { max-width: 1080px; }
.profile-card { margin-bottom: 16px; }
.profile-head { display: flex; align-items: center; gap: 16px; }
.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 26px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
}
.name { font-size: 18px; font-weight: bold; color: #303133; }
.sub { color: #909399; font-size: 13px; margin-top: 2px; }
.tip { color: #909399; font-size: 12px; margin-left: 8px; }
</style>
