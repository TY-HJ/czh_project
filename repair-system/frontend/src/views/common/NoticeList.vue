<template>
  <el-card>
    <template #header><b>公告列表</b></template>
    <el-empty v-if="list.length === 0" description="暂无公告" />
    <el-collapse v-model="activeNames">
      <el-collapse-item v-for="n in list" :key="n.id" :name="n.id">
        <template #title>
          <b>{{ n.title }}</b>
          <span style="margin-left: 12px; color: #999; font-size: 12px">{{ n.createTime }}</span>
        </template>
        <div style="white-space: pre-wrap; color: #444">{{ n.content }}</div>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getNoticePage } from '../../api/common'

const list = ref([])
const activeNames = ref([])

onMounted(async () => {
  const page = await getNoticePage({ current: 1, size: 20 })
  list.value = page.records
  if (list.value.length > 0) activeNames.value = [list.value[0].id]

  // 记录"最近已看公告"时间(取最新公告时间), 通知顶栏熄灭公告红点
  const latest = list.value.length > 0 ? list.value[0].createTime : null
  const seen = localStorage.getItem('noticeSeenAt')
  if (latest && (!seen || latest > seen)) {
    localStorage.setItem('noticeSeenAt', latest)
  }
  window.dispatchEvent(new Event('notice-seen'))
})
</script>
