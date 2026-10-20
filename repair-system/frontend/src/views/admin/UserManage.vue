<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>用户管理</b>
        <div style="display: flex; gap: 8px">
          <el-select v-model="query.role" placeholder="角色" clearable style="width: 120px" @change="load">
            <el-option label="住户" value="RESIDENT" />
            <el-option label="维修工" value="WORKER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 180px" @keyup.enter="load" />
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openEdit(null)">新增用户</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="{ ADMIN: 'danger', WORKER: 'warning', RESIDENT: 'primary' }[row.role]" size="small">
            {{ { ADMIN: '管理员', WORKER: '维修工', RESIDENT: '住户' }[row.role] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="165" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain @click="toggle(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <!-- 新增/编辑对话框 -->
  <el-dialog v-model="editVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="440px">
    <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="80px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="editForm.username" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="editForm.password" type="password" show-password
                  :placeholder="editForm.id ? '留空表示不修改' : '请设置初始密码'" />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model="editForm.realName" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="editForm.phone" />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-select v-model="editForm.role" style="width: 100%">
          <el-option label="住户" value="RESIDENT" />
          <el-option label="维修工" value="WORKER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEdit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPage, addUser, updateUser, toggleUserStatus, deleteUser } from '../../api/user'

const list = ref([])
const total = ref(0)
const query = reactive({ role: null, keyword: '', current: 1, size: 10 })

const editVisible = ref(false)
const editRef = ref()
const editForm = reactive({ id: null, username: '', password: '', realName: '', phone: '', role: 'RESIDENT' })
const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  // 密码只在"新增"时必填,编辑时留空表示不修改(提交时手动校验)
  password: [{ min: 6, max: 20, message: '密码长度6~20位', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function load() {
  const page = await getUserPage(query)
  list.value = page.records
  total.value = Number(page.total)
}

function openEdit(row) {
  editForm.id = row?.id ?? null
  editForm.username = row?.username ?? ''
  editForm.password = ''
  editForm.realName = row?.realName ?? ''
  editForm.phone = row?.phone ?? ''
  editForm.role = row?.role ?? 'RESIDENT'
  editVisible.value = true
}

async function submitEdit() {
  await editRef.value.validate()
  if (!editForm.id && !editForm.password) {
    ElMessage.warning('新增用户请设置初始密码')
    return
  }
  if (editForm.id) {
    await updateUser(editForm.id, editForm)
  } else {
    await addUser(editForm)
  }
  ElMessage.success('保存成功')
  editVisible.value = false
  load()
}

async function toggle(row) {
  await toggleUserStatus(row.id)
  ElMessage.success('操作成功')
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.realName}」吗?`, '警告', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
