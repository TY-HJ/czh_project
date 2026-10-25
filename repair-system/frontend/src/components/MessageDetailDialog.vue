<template>
  <el-dialog v-model="visible" title="消息详情" width="620px" destroy-on-close>
    <template v-if="msg">
      <!-- 消息正文 -->
      <div class="msg-head">
        <el-icon size="20" :color="msg.isRead === 0 ? '#409EFF' : '#C0C4CC'"><ChatDotRound /></el-icon>
        <span class="msg-title">{{ typeText }}</span>
        <el-tag size="small" :type="msg.isRead === 0 ? 'primary' : 'info'" effect="plain">
          {{ msg.isRead === 0 ? '未读' : '已读' }}
        </el-tag>
        <span class="spacer" />
        <span class="msg-time">{{ msg.createTime }}</span>
      </div>
      <div class="msg-body">{{ msg.content }}</div>

      <!-- 关联工单摘要 -->
      <template v-if="msg.orderId">
        <el-divider content-position="left">关联工单</el-divider>
        <div v-loading="loading" class="order-box">
          <template v-if="order">
            <div class="order-row">
              <span class="order-no">{{ order.orderNo }}</span>
              <status-tag :status="order.status" :accept-time="order.workerAcceptTime" />
              <el-tag v-if="order.priority === 'URGENT'" size="small" type="danger" effect="dark">紧急</el-tag>
            </div>
            <div class="order-title">{{ order.title }}</div>
            <el-descriptions :column="2" size="small" border style="margin-top: 10px">
              <el-descriptions-item label="故障类型">{{ order.typeName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="报修位置">{{ order.locationText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="报修人">{{ order.userName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="维修工">{{ order.workerName || '待派单' }}</el-descriptions-item>
              <el-descriptions-item label="提交时间" :span="2">{{ order.submitTime || '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <el-empty v-else-if="!loading" :image-size="60" description="工单信息加载失败或无查看权限" />
        </div>
      </template>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="msg && msg.orderId && order" type="primary" @click="viewOrder">
        查看工单详情
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import StatusTag from './StatusTag.vue'
import { getOrderDetail } from '../api/order'

const emit = defineEmits(['view-order'])

const visible = ref(false)
const msg = ref(null)
const order = ref(null)
const loading = ref(false)

/** 按文案关键字推断消息类型, 仅用于弹窗标题展示 */
const typeText = computed(() => {
  const c = msg.value?.content || ''
  if (c.includes('催办')) return '催办提醒'
  if (c.includes('拒单')) return '拒单通知'
  if (c.includes('改派')) return '改派通知'
  if (c.includes('重新安排')) return '重新派单通知'
  if (c.includes('新的维修任务')) return '派单通知'
  if (c.includes('接单')) return '接单通知'
  if (c.includes('验收')) return '验收通知'
  if (c.includes('评价')) return '评价通知'
  if (c.includes('完成')) return '完工通知'
  if (c.includes('注册')) return '账号通知'
  return '系统通知'
})

/** 打开消息详情, 有关联工单时并行加载摘要 */
async function open(record) {
  msg.value = { ...record }
  order.value = null
  visible.value = true
  if (record.orderId) {
    loading.value = true
    try {
      order.value = await getOrderDetail(record.orderId)
    } catch (e) {
      order.value = null
    } finally {
      loading.value = false
    }
  }
}

function viewOrder() {
  const id = msg.value?.orderId
  visible.value = false
  emit('view-order', id)
}

defineExpose({ open })
</script>

<style scoped>
.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.msg-title { font-weight: 600; font-size: 15px; }
.spacer { flex: 1; }
.msg-time { color: #909399; font-size: 12px; }
.msg-body {
  margin-top: 12px;
  padding: 14px 16px;
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.order-box { min-height: 60px; }
.order-row { display: flex; align-items: center; gap: 8px; }
.order-no { font-family: Consolas, Monaco, monospace; font-size: 14px; color: #606266; }
.order-title { margin-top: 6px; font-size: 15px; font-weight: 600; color: #303133; }
</style>
