<template>
  <el-dialog v-model="visible" title="工单详情" width="640px" destroy-on-close>
    <template v-if="order">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><status-tag :status="order.status" /></el-descriptions-item>
        <el-descriptions-item label="标题">{{ order.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ order.typeName }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ order.locationText }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <el-tag :type="order.priority === 'URGENT' ? 'danger' : 'info'" size="small">
            {{ order.priority === 'URGENT' ? '紧急' : '普通' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报修人">{{ order.userName }}（{{ order.userPhone }}）</el-descriptions-item>
        <el-descriptions-item label="维修工">{{ order.workerName || '待派单' }}</el-descriptions-item>
        <el-descriptions-item label="问题描述" :span="2">{{ order.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="报修照片" :span="2">
          <el-image v-for="(img, i) in order.images" :key="i" :src="img"
                    :preview-src-list="order.images" fit="cover"
                    style="width: 80px; height: 80px; margin-right: 8px; border-radius: 4px" />
          <span v-if="!order.images || order.images.length === 0" style="color: #999">无照片</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="order.handleNote" label="处理说明" :span="2">{{ order.handleNote }}</el-descriptions-item>
        <el-descriptions-item v-if="order.score" label="验收评分" :span="2">
          <el-rate :model-value="order.score" disabled />
          <span style="margin-left: 8px; color: #999">{{ order.evaluationContent }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <h4 style="margin: 16px 0 8px">处理进度</h4>
      <el-timeline>
        <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.createTime" placement="top">
          <b>{{ log.actionText }}</b>
          <span style="margin-left: 8px; color: #999">{{ log.operatorName }}</span>
          <div v-if="log.remark" style="color: #666; font-size: 13px; margin-top: 2px">{{ log.remark }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import StatusTag from './StatusTag.vue'
import { getOrderDetail, getOrderLogs } from '../api/order'

const visible = ref(false)
const order = ref(null)
const logs = ref([])

async function open(orderId) {
  order.value = await getOrderDetail(orderId)
  logs.value = await getOrderLogs(orderId)
  visible.value = true
}

defineExpose({ open })
</script>
