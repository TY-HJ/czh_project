<template>
  <el-tag :type="tagType" disable-transitions>{{ text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ status: { type: Number, required: true } })

const map = {
  0: { text: '待派单', type: 'warning' },
  1: { text: '处理中', type: 'primary' },
  2: { text: '待验收', type: 'info' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已关闭', type: 'danger' }
}
const text = computed(() => map[props.status]?.text || '未知')
const tagType = computed(() => map[props.status]?.type || 'info')
</script>
