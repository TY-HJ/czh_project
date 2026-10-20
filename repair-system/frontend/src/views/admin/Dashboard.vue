<template>
  <div>
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="4" v-for="card in cards" :key="card.label">
        <el-card shadow="hover">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card><template #header><b>报修类型分布</b></template><div ref="typeChart" class="chart" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card><template #header><b>近6个月工单量趋势</b></template><div ref="trendChart" class="chart" /></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card><template #header><b>维修工评分排行</b></template><div ref="rankChart" class="chart" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><b>说明</b></template>
          <el-descriptions :column="1">
            <el-descriptions-item label="平均处理时长">{{ overview.avgDurationHours ?? '-' }} 小时(从提交到维修完成)</el-descriptions-item>
            <el-descriptions-item label="评分说明">评分为住户验收时的1~5星打分平均值</el-descriptions-item>
            <el-descriptions-item label="超时提醒">待派单超24小时 / 处理中超48小时,系统自动发消息催办</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getOverview, getTypeDistribution, getMonthlyTrend, getWorkerRanking } from '../../api/common'

const overview = ref({})
const typeChart = ref()
const trendChart = ref()
const rankChart = ref()

const cards = computed(() => [
  { label: '工单总数', value: overview.value.total ?? 0, color: '#303133' },
  { label: '待派单', value: overview.value.pendingDispatch ?? 0, color: '#E6A23C' },
  { label: '处理中', value: overview.value.processing ?? 0, color: '#409EFF' },
  { label: '待验收', value: overview.value.pendingAcceptance ?? 0, color: '#909399' },
  { label: '已完成', value: overview.value.completed ?? 0, color: '#67C23A' },
  { label: '平均时长(时)', value: overview.value.avgDurationHours ?? 0, color: '#F56C6C' }
])

function renderTypeChart(data) {
  echarts.init(typeChart.value).setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['35%', '65%'],
      label: { formatter: '{b}: {c}单' },
      data: data.map(d => ({ name: d.name, value: d.value }))
    }]
  })
}

function renderTrendChart(data) {
  echarts.init(trendChart.value).setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map(d => d.month) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', smooth: true, areaStyle: {}, data: data.map(d => d.count) }]
  })
}

function renderRankChart(data) {
  // 取平均分,横向条形图
  const sorted = [...data].sort((a, b) => a.avgScore - b.avgScore)
  echarts.init(rankChart.value).setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 80 },
    xAxis: { type: 'value', max: 5 },
    yAxis: { type: 'category', data: sorted.map(d => d.workerName) },
    series: [{
      type: 'bar',
      data: sorted.map(d => d.avgScore),
      label: { show: true, position: 'right', formatter: p => p.value + '分' },
      itemStyle: { color: '#409EFF' }
    }]
  })
}

onMounted(async () => {
  overview.value = await getOverview()
  renderTypeChart(await getTypeDistribution())
  renderTrendChart(await getMonthlyTrend())
  renderRankChart(await getWorkerRanking())
})
</script>

<style scoped>
.stat-value { font-size: 28px; font-weight: bold; text-align: center; }
.stat-label { text-align: center; color: #909399; margin-top: 6px; font-size: 13px; }
.chart { height: 300px; }
</style>
