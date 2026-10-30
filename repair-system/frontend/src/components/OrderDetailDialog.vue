<template>
  <el-dialog v-model="visible" title="工单详情" width="720px" destroy-on-close>
    <div v-loading="loading" style="min-height: 120px">
      <template v-if="order">
        <!-- 头部: 工单号 + 状态 -->
        <div class="head">
          <span class="order-no">{{ order.orderNo }}</span>
          <status-tag :status="order.status" :accept-time="order.workerAcceptTime" />
          <el-tag v-if="order.priority === 'URGENT'" size="small" type="danger" effect="dark">紧急</el-tag>
          <el-tag v-if="order.urgeCount > 0" size="small" type="warning" effect="plain">
            已催办 {{ order.urgeCount }} 次
          </el-tag>
          <span class="spacer" />
          <span class="head-title">{{ order.title }}</span>
        </div>

        <!-- 基础信息 -->
        <el-descriptions :column="2" border size="small" style="margin-top: 12px">
          <el-descriptions-item label="故障类型">{{ order.typeName }}</el-descriptions-item>
          <el-descriptions-item label="报修位置">{{ order.locationText }}</el-descriptions-item>
          <el-descriptions-item label="报修人">{{ order.userName }}（{{ order.userPhone }}）</el-descriptions-item>
          <el-descriptions-item label="维修工">{{ order.workerName || '待派单' }}</el-descriptions-item>
          <el-descriptions-item label="派单方式">
            <span v-if="order.dispatchType === 'AUTO'">智能派单</span>
            <span v-else-if="order.dispatchType === 'MANUAL'">管理员派单</span>
            <span v-else>-</span>
            <span v-if="order.dispatchType === 'AUTO' && order.dispatchScore != null" style="color: #909399">
              （综合得分 {{ order.dispatchScore }}）
            </span>
            <span v-if="order.reassignCount > 0" style="color: #E6A23C">（已改派 {{ order.reassignCount }} 次）</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ order.submitTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="派单时间">{{ order.assignTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="接单时间">{{ order.workerAcceptTime || '尚未接单' }}</el-descriptions-item>
          <el-descriptions-item label="完工时间">{{ order.finishTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="验收时间">{{ order.acceptTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ order.description || '无' }}</el-descriptions-item>
          <el-descriptions-item label="报修照片" :span="2">
            <el-image v-for="(img, i) in order.images" :key="i" :src="img"
                      :preview-src-list="order.images" :initial-index="i" fit="cover"
                      style="width: 80px; height: 80px; margin-right: 8px; border-radius: 4px" />
            <span v-if="!order.images || order.images.length === 0" style="color: #999">无照片</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.handleNote" label="处理说明" :span="2">{{ order.handleNote }}</el-descriptions-item>
          <el-descriptions-item v-if="order.score" label="验收评分" :span="2">
            <el-rate :model-value="order.score" disabled />
            <span style="margin-left: 8px; color: #999">{{ order.evaluationContent }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 处理进度 -->
        <h4 style="margin: 16px 0 8px">处理进度</h4>
        <el-empty v-if="logs.length === 0" description="暂无流转记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.createTime"
                            placement="top" :type="logType(log.action)">
            <b>{{ log.actionText }}</b>
            <span style="margin-left: 8px; color: #999">{{ log.operatorName }}</span>
            <div v-if="log.remark" style="color: #666; font-size: 13px; margin-top: 2px">{{ log.remark }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-else-if="!loading" :image-size="80" description="工单信息加载失败或无查看权限" />
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import StatusTag from './StatusTag.vue'
import { getOrderDetail, getOrderLogs } from '../api/order'

const visible = ref(false)
const loading = ref(false)
const order = ref(null)
const logs = ref([])

/** 打开详情: 先置空避免上一次内容闪现 */
async function open(orderId) {
  order.value = null
  logs.value = []
  visible.value = true
  loading.value = true
  try {
    order.value = await getOrderDetail(orderId)
  } catch (e) {
    // 无权限或已删除: 保持空态提示, 不再请求流转日志
    order.value = null
    loading.value = false
    return
  }
  try {
    logs.value = await getOrderLogs(orderId)
  } catch (e) {
    logs.value = []
  }
  loading.value = false
}

/** 时间轴节点颜色: 完成/派单/异常区分 */
function logType(action) {
  if (['FINISH', 'PASS', 'AUTO_ASSIGN', 'ASSIGN', 'ACCEPT'].includes(action)) return 'success'
  if (['REJECT', 'REFUSE', 'CLOSE', 'CANCEL'].includes(action)) return 'danger'
  if (['URGE', 'REASSIGN'].includes(action)) return 'warning'
  if (action === 'HOLD') return 'info'   // 转人工派单: 中性色, 点击时间轴可看具体原因
  return 'primary'
}

defineExpose({ open })
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}
.order-no { font-family: Consolas, Monaco, monospace; font-size: 15px; color: #606266; }
.spacer { flex: 1; }
.head-title { font-size: 15px; font-weight: 600; color: #303133; }
</style>
