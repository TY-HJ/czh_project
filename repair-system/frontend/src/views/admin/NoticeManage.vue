<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>公告管理</b>
        <el-button type="primary" @click="openEdit(null)">发布公告</el-button>
      </div>
    </template>

    <el-table :data="list" stripe>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="createTime" label="发布时间" width="165" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <el-dialog v-model="editVisible" :title="editForm.id ? '编辑公告' : '发布公告'" width="560px">
    <el-form label-width="60px">
      <el-form-item label="标题"><el-input v-model="editForm.title" maxlength="60" /></el-form-item>
      <el-form-item label="内容">
        <el-input v-model="editForm.content" type="textarea" :rows="6" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNoticePage, addNotice, updateNotice, deleteNotice } from '../../api/common'

const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10 })
const editVisible = ref(false)
const editForm = reactive({ id: null, title: '', content: '' })

async function load() {
  const page = await getNoticePage(query)
  list.value = page.records
  total.value = Number(page.total)
}

function openEdit(row) {
  editForm.id = row?.id ?? null
  editForm.title = row?.title ?? ''
  editForm.content = row?.content ?? ''
  editVisible.value = true
}

async function submit() {
  if (!editForm.title.trim() || !editForm.content.trim()) {
    return ElMessage.warning('请填写标题和内容')
  }
  if (editForm.id) await updateNotice(editForm)
  else await addNotice(editForm)
  ElMessage.success('发布成功')
  editVisible.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗?`, '提示', { type: 'warning' })
  await deleteNotice(row.id)
  load()
}

onMounted(load)
</script>
