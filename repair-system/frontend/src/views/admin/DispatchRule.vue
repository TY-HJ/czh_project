<template>
  <div>
    <!-- 规则说明 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="hd">
          <b>自动派单</b>
          <div class="hd-right">
            <span class="hd-tip">自动派单开关</span>
            <el-switch v-model="form.autoDispatchEnabled" />
            <el-tag :type="form.autoDispatchEnabled ? 'success' : 'info'" size="small">
              {{ form.autoDispatchEnabled ? '已开启' : '已关闭(全部人工派单)' }}
            </el-tag>
          </div>
        </div>
      </template>

      <el-alert type="success" :closable="false" show-icon style="margin-bottom: 14px"
                title="住户提交报修后，系统自动把工单派给最合适的一位师傅；找不到合适的师傅才转人工，并写明原因、通知管理员。" />

      <div class="rule-list">
        <div class="rule-item">
          <div class="idx">1</div>
          <div class="body">
            <div class="name">自动派单开关已打开</div>
            <div class="desc">关闭后所有新工单都进入「待派单」，由管理员手动指派</div>
          </div>
          <el-tag size="small" effect="plain" :type="form.autoDispatchEnabled ? 'success' : 'danger'">
            {{ form.autoDispatchEnabled ? '满足' : '不满足' }}
          </el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">2</div>
          <div class="body">
            <div class="name">有会修这类活的在岗师傅</div>
            <div class="desc">按下面的「师傅技能一览」匹配报修类型，账号被禁用的师傅不算</div>
          </div>
          <el-tag size="small" effect="plain" type="success">提交时判定</el-tag>
        </div>
        <div class="rule-item">
          <div class="idx">3</div>
          <div class="body">
            <div class="name">这位师傅手头没排满</div>
            <div class="desc">正在处理的工单不足 {{ form.maxLoad }} 单才参与自动派单，防止把单压给最忙的人</div>
          </div>
          <el-tag size="small" effect="plain" type="success">提交时判定</el-tag>
        </div>
      </div>

      <div class="foot-tip">
        多位师傅都符合时，按 <b>空闲程度、历史好评、对本楼栋熟悉度</b> 综合打分，派给得分最高的一位；
        师傅<b>拒单</b>后工单退回待派单，由管理员重新指派；处理中超 {{ form.reassignHours }} 小时未完成会自动改派（最多 {{ form.reassignLimit }} 次）。
      </div>
    </el-card>

    <!-- 师傅技能一览 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="hd">
          <b>师傅技能一览</b>
          <span class="hd-tip">在「用户管理 → 技能」中调整；某类型一列全空时，该类工单会转人工</span>
        </div>
      </template>
      <el-table :data="skillRows" border size="small">
        <el-table-column prop="name" label="师傅" width="120" fixed="left" />
        <el-table-column v-for="t in types" :key="t.id" :label="t.typeName" align="center" min-width="70">
          <template #default="{ row }">
            <span v-if="row.skills.includes(t.id)" class="skill-yes">✓</span>
            <span v-else class="skill-no">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在岗' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 参数 -->
    <el-card>
      <template #header>
        <div class="hd">
          <b>参数设置</b>
          <div class="hd-right">
            <el-button @click="load">还原</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="150px" style="max-width: 720px">
        <el-form-item label="单人负荷上限">
          <el-input-number v-model="form.maxLoad" :min="1" :max="50" />
          <span class="hint">正在处理的工单达到该单数的师傅不再被自动派单</span>
        </el-form-item>

        <el-collapse style="margin-top: 8px">
          <el-collapse-item name="adv">
            <template #title><span class="adv-title">高级参数（打分权重与超时改派，保持默认即可）</span></template>
            <el-form-item label="负荷权重">
              <el-input-number v-model="form.wLoad" :min="0" :max="1" :step="0.05" :precision="2" />
              <span class="hint">越高越倾向派给空闲的师傅</span>
            </el-form-item>
            <el-form-item label="历史评分权重">
              <el-input-number v-model="form.wRating" :min="0" :max="1" :step="0.05" :precision="2" />
              <span class="hint">越高越倾向派给好评多的师傅</span>
            </el-form-item>
            <el-form-item label="楼栋熟悉度权重">
              <el-input-number v-model="form.wFamiliarity" :min="0" :max="1" :step="0.05" :precision="2" />
              <span class="hint">越高越倾向派给常跑该楼栋的师傅</span>
            </el-form-item>
            <el-form-item label="紧急单负荷放大">
              <el-input-number v-model="form.urgencyBoost" :min="0" :max="2" :step="0.1" :precision="2" />
              <span class="hint">紧急工单更偏向派给空闲的师傅</span>
            </el-form-item>
            <el-form-item label="熟悉度满分单数">
              <el-input-number v-model="form.familiarityFull" :min="1" :max="50" />
              <span class="hint">在该楼栋完成多少单算"完全熟悉"</span>
            </el-form-item>
            <el-form-item label="无评价默认得分">
              <el-input-number v-model="form.defaultRating" :min="0" :max="1" :step="0.05" :precision="2" />
              <span class="hint">新师傅还没有评价时使用的评分得分（0~1）</span>
            </el-form-item>
            <el-form-item label="自动改派阈值">
              <el-input-number v-model="form.reassignHours" :min="1" :max="720" />
              <span class="hint">处理中超过该小时数仍未完工则自动改派</span>
            </el-form-item>
            <el-form-item label="改派次数上限">
              <el-input-number v-model="form.reassignLimit" :min="0" :max="10" />
              <span class="hint">超过上限不再自动改派，通知管理员介入</span>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDispatchRule, saveDispatchRule } from '../../api/dispatch'
import { getTypes, getUsersByRole, getWorkerSkills } from '../../api/user'

const saving = ref(false)
const types = ref([])
const workers = ref([])
const form = reactive({
  autoDispatchEnabled: true,
  maxLoad: 5,
  wLoad: 0.4,
  wRating: 0.35,
  wFamiliarity: 0.25,
  urgencyBoost: 0.5,
  familiarityFull: 5,
  defaultRating: 0.6,
  reassignHours: 48,
  reassignLimit: 2
})

/** 师傅技能矩阵行: 每位维修工一行, 勾选的类型标 ✓ */
const skillRows = ref([])

async function load() {
  const rule = await getDispatchRule()
  if (rule) {
    Object.keys(form).forEach(k => {
      if (rule[k] !== null && rule[k] !== undefined) form[k] = rule[k]
    })
  }
  types.value = await getTypes()
  const list = await getUsersByRole('WORKER')
  workers.value = list
  skillRows.value = await Promise.all(list.map(async w => ({
    id: w.id,
    name: w.realName,
    status: w.status,
    skills: await getWorkerSkills(w.id).catch(() => [])
  })))
}

async function save() {
  const sum = Number(form.wLoad || 0) + Number(form.wRating || 0) + Number(form.wFamiliarity || 0)
  if (sum <= 0) return ElMessage.warning('三项权重合计必须大于 0')
  saving.value = true
  try {
    await saveDispatchRule({ ...form })
    ElMessage.success('已保存，新提交的工单立即生效')
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
.adv-title { font-size: 13px; color: #606266; }
.skill-yes { color: #67c23a; font-weight: 700; }
.skill-no { color: #dcdfe6; }
</style>
