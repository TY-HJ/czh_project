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
      <el-check-tag :checked="query.status === 4" @change="filterByStatus(4)">
        已关闭<template v-if="counts.closed">({{ counts.closed }})</template>
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

  <!-- 派单 / 改派对话框: 直接展示候选人得分, 管理员按数据选人而不是凭印象 -->
  <el-dialog v-model="assignVisible" :title="isReassign ? '改派维修工' : '派单'" width="780px" top="7vh">
    <el-alert v-if="isReassign" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
              :title="`当前维修工:${assignRow?.workerName || '未知'}(${assignRow?.workerAcceptTime ? '已接单' : '尚未接单'})`"
              description="改派后原师傅会收到取消通知,新师傅需重新接单,超时计时重新开始" />

    <div class="assign-head">
      <span>工单号 <b>{{ assignRow?.orderNo }}</b></span>
      <span>类型：{{ assignRow?.typeName || '-' }}</span>
      <el-tag v-if="assignRow?.priority === 'URGENT'" type="danger" size="small">紧急</el-tag>
      <span class="assign-hint">点表格任意一行选中；「综合得分」已按派单规则加权计算</span>
    </div>

    <el-table ref="candTable" :data="candidates" size="small" v-loading="candLoading" max-height="330"
              highlight-current-row @current-change="pickRow">
      <el-table-column prop="workerName" label="维修工" width="95" />
      <el-table-column label="当前在办" width="85">
        <template #default="{ row }">{{ row.currentLoad }} 单</template>
      </el-table-column>
      <el-table-column label="平均评分" width="85">
        <template #default="{ row }">
          <span v-if="row.avgRating">{{ row.avgRating }} 分</span>
          <span v-else style="color: #c0c4cc">暂无</span>
        </template>
      </el-table-column>
      <el-table-column label="本栋完成" width="85">
        <template #default="{ row }">{{ row.familiarCount }} 单</template>
      </el-table-column>
      <el-table-column label="综合得分" width="90">
        <template #default="{ row }">
          <b :style="{ color: row.skillMatched ? '#303133' : '#c0c4cc' }">{{ row.totalScore }}</b>
        </template>
      </el-table-column>
      <el-table-column label="标记" min-width="150">
        <template #default="{ row }">
          <el-tag v-if="!row.skillMatched" type="danger" size="small">缺技能</el-tag>
          <el-tag v-else-if="row.overload" type="warning" size="small">已满负荷</el-tag>
          <el-tag v-else-if="row.workerId === recommendedId" type="success" size="small">推荐</el-tag>
          <el-tag v-if="row.currentWorker" type="info" size="small" style="margin-left: 4px">当前</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" width="120" />
    </el-table>

    <el-empty v-if="!candLoading && !candidates.length" description="暂无在岗维修工，请先在用户管理中启用账号" :image-size="70" />

    <template #footer>
      <div class="assign-foot">
        <span v-if="picked">
          已选择：<b>{{ picked.workerName }}</b>
          <el-tag v-if="!picked.skillMatched" type="danger" size="small">缺技能</el-tag>
          <el-tag v-else-if="picked.overload" type="warning" size="small">已满负荷</el-tag>
        </span>
        <span v-else class="assign-foot-tip">请在上表中选择一位维修工</span>
      </div>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!picked" @click="submitAssign">
        {{ isReassign ? '确认改派' : '确认派单' }}
      </el-button>
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
import { getAssignCandidates } from '../../api/dispatch'
import { getOverview } from '../../api/common'

const route = useRoute()
const detailRef = ref()
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, keyword: '', current: 1, size: 10 })
const counts = ref({ total: 0, pendingDispatch: 0, processing: 0, pendingAcceptance: 0, completed: 0, closed: 0 })

const assignVisible = ref(false)
const assignRow = ref(null)
const candidates = ref([])
const picked = ref(null)
const candLoading = ref(false)
const candTable = ref()
// 处理中的工单再次派单 = 改派(换师傅)
const isReassign = computed(() => assignRow.value?.status === 1)
// 推荐位: 第一个"技能匹配且未满负荷"的人(后端已把它排在前面)
const recommendedId = computed(() => {
  const top = candidates.value.find(c => c.skillMatched && !c.overload)
  return top ? top.workerId : null
})

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
  picked.value = null
  candidates.value = []
  assignVisible.value = true
  // 每次打开都重新拉: 负荷和评分是实时的, 用缓存会派给刚满负荷的师傅
  candLoading.value = true
  try {
    candidates.value = await getAssignCandidates(row.id)
  } catch (e) {
    candidates.value = []
  } finally {
    candLoading.value = false
  }
}

function pickRow(row) {
  if (!row) return
  // 改派时不能选原师傅(后端也会拦), 这里提前拦掉并把高亮退回去
  if (isReassign.value && row.workerId === assignRow.value.workerId) {
    ElMessage.warning(`该工单当前就派给${row.workerName}，请选择其他师傅`)
    if (candTable.value) candTable.value.setCurrentRow(picked.value)
    return
  }
  picked.value = row
}

/**
 * 派单/改派。
 * 该师傅未配置本工单类型的技能时后端返回 4001, 这里弹确认框:
 * 确认则带 force=true 强制派出(避免"该类型只有一个人会修、他又被禁用"导致工单永久卡死)
 */
async function doAssign(force) {
  const id = assignRow.value.id
  try {
    await assignOrder(id, { workerId: picked.value.workerId, force })
    return true
  } catch (e) {
    if (e.code !== 4001 || force) return false
    try {
      await ElMessageBox.confirm(e.message + '。确认后将强制派出,建议事后补充该师傅的技能配置。', '技能不匹配', {
        type: 'warning', confirmButtonText: '仍然派单', cancelButtonText: '重新选择'
      })
    } catch (_) {
      return false
    }
    return doAssign(true)
  }
}

async function submitAssign() {
  if (!picked.value) {
    ElMessage.warning('请选择维修工')
    return
  }
  const reassign = isReassign.value
  const ok = await doAssign(false)
  if (!ok) return
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

.assign-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}

.assign-hint {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.assign-foot {
  float: left;
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 32px;
  font-size: 13px;
  color: #606266;
}

.assign-foot-tip {
  font-size: 12px;
  color: #909399;
}
</style>
