<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
        <b>工单管理</b>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="query.keyword" placeholder="工单号/标题" clearable style="width: 200px" @keyup.enter="load" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="load">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="Number(k)" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" stripe>
      <el-table-column prop="orderNo" label="工单号" width="125" />
      <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
      <el-table-column prop="typeName" label="类型" width="90" />
      <el-table-column prop="locationText" label="位置" width="130" />
      <el-table-column prop="userName" label="报修人" width="85" />
      <el-table-column label="紧急" width="65">
        <template #default="{ row }">
          <el-tag v-if="row.priority === 'URGENT'" type="danger" size="small">急</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="85"><template #default="{ row }"><status-tag :status="row.status" /></template></el-table-column>
      <el-table-column prop="workerName" label="维修工" width="85" />
      <el-table-column label="催办" width="65">
        <template #default="{ row }">
          <el-badge v-if="row.urgeCount > 0" :value="row.urgeCount" type="danger" />
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="155" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="detailRef.open(row.id)">详情</el-button>
          <el-button v-if="row.status === 0" size="small" type="primary" @click="openAssign(row)">派单</el-button>
          <el-button v-if="row.status === 1 || row.status === 2" size="small" type="warning" @click="urge(row)">催办</el-button>
          <el-button v-if="row.status === 0 || row.status === 1 || row.status === 2" size="small" type="danger" plain @click="close(row)">关闭</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <order-detail-dialog ref="detailRef" />

  <!-- 派单对话框 -->
  <el-dialog v-model="assignVisible" title="派单" width="400px">
    <el-form label-width="80px">
      <el-form-item label="工单号">{{ assignRow?.orderNo }}</el-form-item>
      <el-form-item label="维修工">
        <el-select v-model="workerId" placeholder="请选择维修工" style="width: 100%">
          <el-option v-for="w in workers" :key="w.id" :label="`${w.realName}（${w.phone || '无电话'}）`" :value="w.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAssign">确认派单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../../components/StatusTag.vue'
import OrderDetailDialog from '../../components/OrderDetailDialog.vue'
import { getAdminOrders, assignOrder, closeOrder, urgeOrder } from '../../api/order'
import { getUsersByRole } from '../../api/user'

const statusMap = { 0: '待派单', 1: '处理中', 2: '待验收', 3: '已完成', 4: '已关闭' }
const detailRef = ref()
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, keyword: '', current: 1, size: 10 })

const assignVisible = ref(false)
const assignRow = ref(null)
const workerId = ref(null)
const workers = ref([])

async function load() {
  const page = await getAdminOrders(query)
  list.value = page.records
  total.value = Number(page.total)
}

async function openAssign(row) {
  assignRow.value = row
  workerId.value = null
  if (workers.value.length === 0) {
    workers.value = await getUsersByRole('WORKER')
  }
  assignVisible.value = true
}

async function submitAssign() {
  if (!workerId.value) {
    ElMessage.warning('请选择维修工')
    return
  }
  await assignOrder(assignRow.value.id, { workerId: workerId.value })
  ElMessage.success('派单成功,已通知维修工')
  assignVisible.value = false
  load()
}

async function urge(row) {
  await urgeOrder(row.id)
  ElMessage.success('已催办')
  load()
}

async function close(row) {
  await ElMessageBox.confirm('关闭后工单不再流转,确定关闭吗?', '提示', { type: 'warning' })
  await closeOrder(row.id)
  ElMessage.success('已关闭')
  load()
}

onMounted(load)
</script>
