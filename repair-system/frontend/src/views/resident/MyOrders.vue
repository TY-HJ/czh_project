<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>我的报修</b>
        <el-radio-group v-model="query.status" @change="load">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button v-for="(v, k) in statusMap" :key="k" :value="Number(k)">{{ v }}</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <el-table :data="list" stripe>
      <el-table-column prop="orderNo" label="工单号" width="130" />
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
      <el-table-column prop="typeName" label="类型" width="100" />
      <el-table-column prop="locationText" label="位置" width="140" />
      <el-table-column label="紧急" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.priority === 'URGENT'" type="danger" size="small">紧急</el-tag>
          <span v-else style="color:#999">普通</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="95"><template #default="{ row }"><status-tag :status="row.status" :accept-time="row.workerAcceptTime" /></template></el-table-column>
      <el-table-column prop="workerName" label="维修工" width="90" />
      <el-table-column prop="submitTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="detailRef.open(row.id)">详情</el-button>
          <el-button v-if="row.status === 0 || row.status === 1" size="small" type="warning" @click="urge(row)">催办</el-button>
          <el-button v-if="row.status === 0" size="small" type="danger" plain @click="cancel(row)">撤销</el-button>
          <el-button v-if="row.status === 2" size="small" type="success" @click="openAccept(row)">验收</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 16px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current" @current-change="load" />
  </el-card>

  <order-detail-dialog ref="detailRef" />

  <!-- 验收对话框 -->
  <el-dialog v-model="acceptVisible" title="验收维修结果" width="440px">
    <el-form label-width="80px">
      <el-form-item label="验收结果">
        <el-radio-group v-model="acceptForm.pass">
          <el-radio :value="true">通过</el-radio>
          <el-radio :value="false">不通过(退回重修)</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="acceptForm.pass" label="评分">
        <el-rate v-model="acceptForm.score" show-text :texts="['很差', '较差', '一般', '满意', '非常满意']" />
      </el-form-item>
      <el-form-item :label="acceptForm.pass ? '评语' : '原因'">
        <el-input v-model="acceptForm.content" type="textarea" :rows="3"
                  :placeholder="acceptForm.pass ? '选填' : '请说明哪里没修好,便于师傅重新处理'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="acceptVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAccept">确认验收</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../../components/StatusTag.vue'
import OrderDetailDialog from '../../components/OrderDetailDialog.vue'
import { getMyOrders, cancelOrder, urgeOrder, acceptOrder } from '../../api/order'

const statusMap = { 0: '待派单', 1: '处理中', 2: '待验收', 3: '已完成', 4: '已关闭' }
const detailRef = ref()
const list = ref([])
const total = ref(0)
const query = reactive({ status: null, current: 1, size: 10 })

const acceptVisible = ref(false)
const acceptOrderId = ref(null)
const acceptForm = reactive({ pass: true, score: 5, content: '' })

async function load() {
  const page = await getMyOrders(query)
  list.value = page.records
  total.value = Number(page.total)
}

async function urge(row) {
  await urgeOrder(row.id)
  ElMessage.success('已催办,管理员和维修工会收到提醒')
  load()
}

async function cancel(row) {
  await ElMessageBox.confirm('确定撤销该报修吗?', '提示', { type: 'warning' })
  await cancelOrder(row.id)
  ElMessage.success('已撤销')
  load()
}

function openAccept(row) {
  acceptOrderId.value = row.id
  acceptForm.pass = true
  acceptForm.score = 5
  acceptForm.content = ''
  acceptVisible.value = true
}

async function submitAccept() {
  if (acceptForm.pass && !acceptForm.score) {
    ElMessage.warning('请打分')
    return
  }
  await acceptOrder(acceptOrderId.value, acceptForm)
  ElMessage.success(acceptForm.pass ? '验收通过,感谢您的评价' : '已退回,等待师傅重新处理')
  acceptVisible.value = false
  load()
}

onMounted(load)
</script>
