<template>
  <div>
    <!-- 判定规则 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="hd">
          <b>自动派单判定规则</b>
          <div class="hd-right">
            <span class="hd-tip">自动派单总开关</span>
            <el-switch v-model="form.autoDispatchEnabled" />
            <el-tag :type="form.autoDispatchEnabled ? 'success' : 'info'" size="small">
              {{ form.autoDispatchEnabled ? '已开启' : '全部转人工' }}
            </el-tag>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
                title="下面 5 个条件全部满足才会自动派单；任一不满足即转人工，系统会在工单时间轴写明原因，并给管理员发消息。" />

      <div class="rule-list">
        <div class="rule-item">
          <div class="idx">1</div>
          <div class="body">
            <div class="name">自动派单总开关已打开</div>
            <div class="desc">关闭后所有工单一律进入待派单，由管理员人工派单</div>
          </div>
          <el-tag size="small" effect="plain" :type="form.autoDispatchEnabled ? 'success' : 'danger'">
            {{ form.autoDispatchEnabled ? '满足' : '不满足' }}
          </el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">2</div>
          <div class="body">
            <div class="name">该报修类型允许自动派单</div>
            <div class="desc">
              在「字典管理」按类型设置；当前人工派单类型：
              <b v-if="manualTypes.length">{{ manualTypes.join('、') }}</b>
              <span v-else>无（全部类型可自动派单）</span>
            </div>
          </div>
          <el-tag size="small" effect="plain" :type="manualTypes.length ? 'warning' : 'success'">
            {{ manualTypes.length ? manualTypes.length + '类需人工' : '全部自动' }}
          </el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">3</div>
          <div class="body">
            <div class="name">存在技能匹配且在岗的维修工</div>
            <div class="desc">维修工技能在「用户管理 → 技能」中配置，账号禁用或技能不匹配都不参与派单</div>
          </div>
          <el-tag size="small" effect="plain" type="success">实时判定</el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">4</div>
          <div class="body">
            <div class="name">至少一人未满负荷</div>
            <div class="desc">未完成（处理中+待验收）工单数达到 {{ form.maxLoad }} 单的师傅不再自动接单</div>
          </div>
          <el-tag size="small" effect="plain" type="success">实时判定</el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">5</div>
          <div class="body">
            <div class="name">可派候选人数不少于 {{ form.minCandidates }} 人</div>
            <div class="desc">候选太少时宁可人工确认，避免把单派给唯一且不合适的人</div>
          </div>
          <el-tag size="small" effect="plain" type="success">实时判定</el-tag>
        </div>
      </div>

      <div class="foot-tip">
        补充约定：维修工<b>拒单</b>后工单退回待派单，系统不会自动改派（由管理员决定）；处理中超时自动改派只对
        「允许自动派单」的类型生效，且最多改派 {{ form.reassignLimit }} 次。
      </div>
    </el-card>

    <!-- 可调参数 -->
    <el-card>
      <template #header>
        <div class="hd">
          <b>可调参数</b>
          <div class="hd-right">
            <el-button @click="load">还原</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="160px" style="max-width: 820px">
        <el-divider content-position="left">派单门槛</el-divider>
        <el-form-item label="单人工单负荷上限">
          <el-input-number v-model="form.maxLoad" :min="1" :max="50" />
          <span class="hint">未完成工单数达到该值的师傅不再被自动派单</span>
        </el-form-item>
        <el-form-item label="可派候选人数下限">
          <el-input-number v-model="form.minCandidates" :min="1" :max="10" />
          <span class="hint">1 = 只要有人能接就自动派；2 = 至少两人可派才自动派</span>
        </el-form-item>

        <el-divider content-position="left">打分权重（综合得分越高越优先）</el-divider>
        <el-form-item label="负荷权重">
          <el-input-number v-model="form.wLoad" :min="0" :max="1" :step="0.05" :precision="2" />
          <span class="hint">当前空闲程度，权重越高越倾向派给闲的师傅</span>
        </el-form-item>
        <el-form-item label="历史评分权重">
          <el-input-number v-model="form.wRating" :min="0" :max="1" :step="0.05" :precision="2" />
          <span class="hint">历史验收平均分，权重越高越倾向派给评价好的师傅</span>
        </el-form-item>
        <el-form-item label="楼栋熟悉度权重">
          <el-input-number v-model="form.wFamiliarity" :min="0" :max="1" :step="0.05" :precision="2" />
          <span class="hint">在该楼栋完成过的单数，权重越高越倾向派给熟悉该楼的师傅</span>
        </el-form-item>
        <el-form-item label="权重合计">
          <el-tag :type="weightSum > 0 ? 'success' : 'danger'" effect="plain">{{ weightSum.toFixed(2) }}</el-tag>
          <span class="hint">三项合计需大于 0（不要求等于 1，系统按相对权重计算）</span>
        </el-form-item>
        <el-form-item label="紧急单负荷放大系数">
          <el-input-number v-model="form.urgencyBoost" :min="0" :max="2" :step="0.1" :precision="2" />
          <span class="hint">紧急工单的负荷权重 = 负荷权重 ×(1 + 该系数)，让紧急单优先给闲人</span>
        </el-form-item>
        <el-form-item label="熟悉度满分单数">
          <el-input-number v-model="form.familiarityFull" :min="1" :max="50" />
          <span class="hint">在该楼栋完成多少单算"完全熟悉"</span>
        </el-form-item>
        <el-form-item label="无评价默认得分">
          <el-input-number v-model="form.defaultRating" :min="0" :max="1" :step="0.05" :precision="2" />
          <span class="hint">新师傅还没有评价时使用的评分得分（0~1）</span>
        </el-form-item>

        <el-divider content-position="left">超时改派</el-divider>
        <el-form-item label="自动改派阈值">
          <el-input-number v-model="form.reassignHours" :min="1" :max="720" />
          <span class="hint">处理中超过该小时数仍未完工则自动改派给下一位候选</span>
        </el-form-item>
        <el-form-item label="改派次数上限">
          <el-input-number v-model="form.reassignLimit" :min="0" :max="10" />
          <span class="hint">超过上限不再自动改派，改为通知管理员介入</span>
        </el-form-item>
        <el-form-item label="得分明细存档条数">
          <el-input-number v-model="form.topN" :min="1" :max="20" />
          <span class="hint">记录前 N 名候选人得分，便于事后解释"当时为什么派给他"</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDispatchRule, saveDispatchRule } from '../../api/dispatch'
import { getTypes } from '../../api/user'

const saving = ref(false)
const types = ref([])
const form = reactive({
  autoDispatchEnabled: true,
  maxLoad: 5,
  minCandidates: 1,
  wLoad: 0.4,
  wRating: 0.35,
  wFamiliarity: 0.25,
  urgencyBoost: 0.5,
  familiarityFull: 5,
  defaultRating: 0.6,
  reassignHours: 48,
  reassignLimit: 2,
  topN: 5
})

const weightSum = computed(() =>
  Number(form.wLoad || 0) + Number(form.wRating || 0) + Number(form.wFamiliarity || 0))
const manualTypes = computed(() => types.value.filter(t => t.autoDispatch === false).map(t => t.typeName))

async function load() {
  const rule = await getDispatchRule()
  if (rule) {
    Object.keys(form).forEach(k => {
      if (rule[k] !== null && rule[k] !== undefined) form[k] = rule[k]
    })
  }
  types.value = await getTypes()
}

async function save() {
  if (weightSum.value <= 0) return ElMessage.warning('三项权重合计必须大于 0')
  saving.value = true
  try {
    await saveDispatchRule({ ...form })
    ElMessage.success('派单规则已保存，新提交的工单立即生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; }
.hd-right { display: flex; align-items: center; gap: 8px; }
.hd-tip { color: #909399; font-size: 13px; }
.rule-list { display: flex; flex-direction: column; gap: 10px; }
.rule-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: #f7f9fc; border-radius: 6px; }
.idx { width: 22px; height: 22px; flex: none; border-radius: 50%; background: #409eff; color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.body { flex: 1; }
.name { font-size: 14px; color: #303133; font-weight: 600; }
.desc { font-size: 12px; color: #909399; margin-top: 2px; }
.foot-tip { margin-top: 12px; font-size: 12px; color: #909399; line-height: 1.7; }
.hint { margin-left: 10px; font-size: 12px; color: #909399; }
</style>
