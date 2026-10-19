<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <b>消息中心</b>
        <el-button size="small" @click="readAll">全部标记已读</el-button>
      </div>
    </template>

    <el-empty v-if="list.length === 0" description="暂无消息" />
    <div v-for="msg in list" :key="msg.id" class="msg-item" :class="{ unread: msg.isRead === 0 }"
         @click="read(msg)">
      <el-badge is-dot :hidden="msg.isRead === 1" class="dot">
        <el-icon size="18"><ChatDotRound /></el-icon>
      </el-badge>
      <span class="content">{{ msg.content }}</span>
      <span class="time">{{ msg.createTime }}</span>
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getMessages, readAllMessages, readMessage } from '../../api/common'

const list = ref([])

async function load() {
  list.value = await getMessages()
}

async function read(msg) {
  if (msg.isRead === 0) {
    await readMessage(msg.id)
    msg.isRead = 1
  }
}

async function readAll() {
  await readAllMessages()
  load()
}

onMounted(load)
</script>

<style scoped>
.msg-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.msg-item:hover { background: #f9f9f9; }
.msg-item.unread .content { font-weight: bold; }
.content { flex: 1; font-size: 14px; }
.time { color: #999; font-size: 12px; }
</style>
