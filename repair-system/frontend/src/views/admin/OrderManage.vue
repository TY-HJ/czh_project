<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
        <b>工单管理</b>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input v-model="query.keyword" placeholder="工单号/标题" clearable style="width: 200px" @keyup.enter="load" />
          <el-button type="primary" @click="load">查询</el-button>
        </div>
      </div>
    </template>

    <!-- 状态快捷筛选: 带各状态数量, 帮管理员一眼看到待处理的量 -->
    <div class="status-chips">
      <el-check-tag :checked="query.status === null" @change="filterByStatus(null)">全部({{ counts.total }})</el-check-tag>
      <el-check-tag :checked="query.status === 0" @change="filterByStatus(0)">
        待派单<template v-if="counts.pendingDispatch">({{ counts.pendingDispatch }})</template>
      </el-check-tag>
      <el-check-tag :checked="query.status === 1" @change="filterByStatus(1)">
        处理中<template v-if="counts.processing">({{ counts.processing }})</template>
      </el-check-tag>
      <el-check-tag :checked="query.status === 2" @change="filterByStatus(2)">
        待验收<template v-if="counts.pendingAcceptance">({{ counts.pendingAcceptance }})</template>
      </el-check-tag>
      <el-check-tag :checked="query.status === 3" @change="filterByStatus(3)">
        已完成<template v-if="counts.completed">({{ counts.completed }})</template>
      </el-check-tag>
    </div>

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
      <el-table-column label="状态" width="95">
        <template #default="{ row }"><status-tag :status="row.status" :accept-time="row.workerAcceptTime" /></template>
      </el-table-column>
      <el-table-column label="时效" width="110">
        <template #default="{ row }">
          <el-tooltip v-if="overdueText(row)" :content="overdueText(row).tip" placement="top">
            <el-tag type="danger" size="small" effect="dark">{{ overdueText(row).label }}</el-tag>
          </el-tooltip>
          <span v-else style="color:#999;font-size:12px">正常</span>
        </template>
      </el-table-column>
      <el-table-column prop="workerName" label="维修工" width="85" />
      <el-table-column label="催办" width="65">
        <template #default="{ row }">
          <el-badge v-if="row.urgeCount > 0" :value="row.urgeCount" type="danger" />
          <span v-else style="color:#999">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="155" />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="detailRef.open(row.id)">详情</el-button>
          <el-button v-if="row.status === 0" size="small" type="primary" @click="openAssign(row)">派单</el-button>
          <el-button v-else-if="row.status === 1" size="small" type="primary" plain @click="openAssign(row)">改派</el-button>
          <el-button v-if="row.status === 0 || row.status === 1" size="small" type="warning" @click="urge(row)">催办</el-button>
          <el-button v-if="row.status === 0 || row.status === 1 || row.status === 2" size="small" type="danger" plain @click="close(row)">关闭</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <order-detail-dialog ref="detailRef" />

  <!-- 派单 / 改派对话框 -->
  <el-dialog v-model="assignVisible" :title="isReassign ? '改派维修工' : '派单'" width="420px">
    <el-alert v-if="isReassign" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
              :title="`当前维修工:${assignRow?.workerName || '未知'}(${assignRow?.workerAcceptTime ? '已接单' : '尚未接单'})`"
              description="改派后原师傅会收到取消通知,新师傅需重新接单,超时计时重新开始" />
    <el-form label-width="80px">
      <el-form-item label="工单号">{{ assignRow?.orderNo }}</el-form-item>
      <el-form-item label="维修工">
        <el-select v-model="workerId" :placeholder="isReassign ? '请选择新的维修工' : '请选择维修工'" style="width: 100%">
          <el-option v-for="w in workers" :key="w.id" :label="`${w.realName}（${w.phone || '无电话'}）`"
                     :value="w.id" :disabled="isReassign && w.id === assignRow?.workerId" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAssign">{{ isReassign ? '确认改派' : '确认派单' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import StatusTag from '../../components/StatusTag.vue'
import OrderDetailDialog from '../../components/OrderDetailDialog.vue'
import { getAdminOrders, assignOrder, closeOrder, urgeOrder } from '../../api/order'
import { getUsersByRole } from '../../api/user'
import { getOverview } from '../../api/common'

const route = useRoute()
const detailRef = ref()
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, keyword: '', current: 1, size: 10 })
const counts = ref({ total: 0, pendingDispatch: 0, processing: 0, pendingAcceptance: 0, completed: 0 })

const assignVisible = ref(false)
const assignRow = ref(null)
const workerId = ref(null)
const workers = ref([])
// 处理中的工单再次派单 = 改派(换师傅)
const isReassign = computed(() => assignRow.value?.status === 1)

async function load() {
  const page = await getAdminOrders(query)
  list.value = page.records
  total.value = Number(page.total)
}

// 各状态数量(供快捷筛选展示), 数据来自统计接口
async function loadCounts() {
  try {
    counts.value = await getOverview()
  } catch (e) { /* 统计失败不阻塞列表 */ }
}

function filterByStatus(status) {
  query.status = status
  query.current = 1
  load()
}

// 超时判断: 待派单超24h / 处理中超48h, 与后端定时催办口径一致
function overdueText(row) {
  const now = dayjs()
  if (row.status === 0 && row.submitTime && now.diff(dayjs(row.submitTime), 'hour', true) > 24) {
    return { label: '超24h未派', tip: '该工单已提交超过24小时仍未派单,请尽快处理' }
  }
  if (row.status === 1 && row.assignTime && now.diff(dayjs(row.assignTime), 'hour', true) > 48) {
    return { label: '超48h未完成', tip: '该工单派单后已超过48小时未完成,请关注' }
  }
  return null
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
  const reassign = isReassign.value
  await assignOrder(assignRow.value.id, { workerId: workerId.value })
  ElMessage.success(reassign ? '改派成功,已通知新旧两位师傅' : '派单成功,已通知维修工')
  assignVisible.value = false
  load()
  loadCounts()
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
  loadCounts()
}

onMounted(() => {
  // 支持从统计大屏等入口带状态跳转过来, 如 /order-manage?status=0
  const qs = Number(route.query.status)
  if (!Number.isNaN(qs) && [0, 1, 2, 3, 4].includes(qs)) {
    query.status = qs
  }
  load()
  loadCounts()
})
</script>

<style scoped>
.status-chips {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  align-items: center;
}
</style>
