<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>维修任务</b>
        <el-radio-group v-model="query.status" @change="load">
          <el-radio-button :value="null">进行中</el-radio-button>
          <el-radio-button :value="1">处理中</el-radio-button>
          <el-radio-button :value="2">待用户验收</el-radio-button>
          <el-radio-button :value="3">已完成</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <el-table :data="list" stripe>
      <el-table-column prop="orderNo" label="工单号" width="130" />
      <el-table-column prop="title" label="标题" min-width="130" show-overflow-tooltip />
      <el-table-column prop="typeName" label="类型" width="95" />
      <el-table-column prop="locationText" label="位置" width="140" />
      <el-table-column prop="userName" label="报修人" width="90" />
      <el-table-column prop="userPhone" label="联系电话" width="120" />
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
      <el-table-column label="评分" width="140">
        <template #default="{ row }">
          <el-rate v-if="row.score" :model-value="row.score" disabled size="small" />
          <span v-else style="color:#999">未评价</span>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="215" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="detailRef.open(row.id)">详情</el-button>
          <template v-if="waitingAccept(row)">
            <el-button size="small" type="primary" @click="doAccept(row)">接单</el-button>
            <el-button size="small" type="danger" plain @click="openRefuse(row)">拒单</el-button>
          </template>
          <el-button v-else-if="row.status === 1" size="small" type="success" @click="openFinish(row)">完成维修</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <order-detail-dialog ref="detailRef" />

  <!-- 完成维修对话框 -->
  <el-dialog v-model="finishVisible" title="填写处理说明" width="440px">
    <el-input v-model="finishNote" type="textarea" :rows="4"
              placeholder="描述维修内容,如:更换了阀芯,已测试正常" />
    <template #footer>
      <el-button @click="finishVisible = false">取消</el-button>
      <el-button type="primary" @click="submitFinish">确认完成</el-button>
    </template>
  </el-dialog>

  <!-- 拒单对话框 -->
  <el-dialog v-model="refuseVisible" title="拒单" width="460px">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
              title="拒单后工单退回待派单,由管理员重新安排师傅,请说明原因" />
    <el-input v-model="refuseReason" type="textarea" :rows="3" maxlength="200" show-word-limit
              placeholder="如:非本人负责区域 / 缺少配件 / 今日休假" />
    <template #footer>
      <el-button @click="refuseVisible = false">取消</el-button>
      <el-button type="danger" @click="submitRefuse">确认拒单</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import StatusTag from '../../components/StatusTag.vue'
import OrderDetailDialog from '../../components/OrderDetailDialog.vue'
import { getWorkerOrders, finishOrder, acceptTask, refuseOrder } from '../../api/order'

const detailRef = ref()
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, current: 1, size: 10 })

const finishVisible = ref(false)
const finishOrderId = ref(null)
const finishNote = ref('')

const refuseVisible = ref(false)
const refuseOrderId = ref(null)
const refuseReason = ref('')

async function load() {
  const page = await getWorkerOrders(query)
  list.value = page.records
  total.value = Number(page.total)
}

/** 已派单但还没接单: 可以接单或拒单 */
function waitingAccept(row) {
  return row.status === 1 && !row.workerAcceptTime
}

// 超时提醒: 处理中超48小时(与系统自动改派/催办口径一致)
function overdueText(row) {
  if (row.status === 1 && row.assignTime && dayjs().diff(dayjs(row.assignTime), 'hour', true) > 48) {
    return { label: '超48h未完成', tip: '该任务派单后已超过48小时未完成,请尽快处理' }
  }
  return null
}

async function doAccept(row) {
  await acceptTask(row.id)
  ElMessage.success('已接单,请及时上门维修')
  load()
}

function openRefuse(row) {
  refuseOrderId.value = row.id
  refuseReason.value = ''
  refuseVisible.value = true
}

async function submitRefuse() {
  if (!refuseReason.value.trim()) {
    ElMessage.warning('请填写拒单原因')
    return
  }
  await refuseOrder(refuseOrderId.value, { reason: refuseReason.value.trim() })
  ElMessage.success('已拒单,工单已退回管理员重新派单')
  refuseVisible.value = false
  load()
}

function openFinish(row) {
  finishOrderId.value = row.id
  finishNote.value = ''
  finishVisible.value = true
}

async function submitFinish() {
  if (!finishNote.value.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  await finishOrder(finishOrderId.value, { handleNote: finishNote.value })
  ElMessage.success('已标记完成,等待用户验收')
  finishVisible.value = false
  load()
}

onMounted(load)
</script>
