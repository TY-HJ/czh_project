<template>
  <el-tag :type="tagType" disable-transitions>{{ text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 工单状态标签
 * acceptTime 可选: 传入后按"是否已接单"把状态1拆成「待接单 / 维修中」,
 * 让住户和维修工都能看清工单卡在谁手里(不改变后端状态码)。
 */
const props = defineProps({
  status: { type: Number, required: true },
  acceptTime: { type: String, default: null }
})

const map = {
  0: { text: '待派单', type: 'warning' },
  1: { text: '处理中', type: 'primary' },
  2: { text: '待验收', type: 'info' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已关闭', type: 'danger' }
}

const isWaitingAccept = computed(() => props.status === 1 && !props.acceptTime)
const text = computed(() => {
  if (isWaitingAccept.value) return '待接单'
  return map[props.status]?.text || '未知'
})
const tagType = computed(() => {
  if (isWaitingAccept.value) return 'warning'
  return map[props.status]?.type || 'info'
})
</script>
