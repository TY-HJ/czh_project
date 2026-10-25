<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
        <b>消息中心</b>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-radio-group v-model="query.isRead" @change="reload">
            <el-radio-button :value="null">全部</el-radio-button>
            <el-radio-button :value="0">
              未读<el-badge v-if="unreadTotal > 0" :value="unreadTotal" style="margin-left: 4px" />
            </el-radio-button>
            <el-radio-button :value="1">已读</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="readAll">全部标记已读</el-button>
        </div>
      </div>
    </template>

    <el-empty v-if="list.length === 0" description="暂无消息" />
    <div v-for="msg in list" :key="msg.id" class="msg-item" :class="{ unread: msg.isRead === 0 }"
         @click="read(msg)">
      <el-badge is-dot :hidden="msg.isRead === 1" class="dot">
        <el-icon size="18"><ChatDotRound /></el-icon>
      </el-badge>
      <span class="content">
        {{ msg.content }}
        <el-tag v-if="msg.orderId" size="small" type="info" effect="plain" style="margin-left: 6px">
          点击查看详情
        </el-tag>
      </span>
      <span class="time">{{ msg.createTime }}</span>
    </div>

    <el-pagination v-if="total > query.size" style="margin-top: 16px; justify-content: flex-end"
                   layout="total, prev, pager, next"
                   :total="total" :page-size="query.size" v-model:current-page="query.current"
                   @current-change="load" />
  </el-card>

  <message-detail-dialog ref="msgDetailRef" @view-order="openOrder" />
  <order-detail-dialog ref="detailRef" />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getMessages, readAllMessages, readMessage, getUnreadCount } from '../../api/common'
import OrderDetailDialog from '../../components/OrderDetailDialog.vue'
import MessageDetailDialog from '../../components/MessageDetailDialog.vue'

const list = ref([])
const total = ref(0)
const unreadTotal = ref(0)
const detailRef = ref()
const msgDetailRef = ref()
const query = reactive({ isRead: null, current: 1, size: 10 })

async function load() {
  const page = await getMessages(query)
  list.value = page.records
  total.value = Number(page.total)
}

async function refreshUnread() {
  unreadTotal.value = Number(await getUnreadCount()) || 0
}

// 切换筛选时回到第一页
async function reload() {
  query.current = 1
  await load()
  refreshUnread()
}

// 点击消息: 先标记已读(红点消失), 再弹出消息详情
async function read(msg) {
  if (msg.isRead === 0) {
    await readMessage(msg.id)
    msg.isRead = 1
    refreshUnread()
  }
  msgDetailRef.value.open(msg)
}

// 消息弹窗里点"查看工单详情": 关闭消息弹窗, 打开工单详情
function openOrder(orderId) {
  if (orderId) {
    detailRef.value.open(orderId)
  }
}

async function readAll() {
  await readAllMessages()
  load()
  refreshUnread()
}

onMounted(() => {
  load()
  refreshUnread()
})
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
