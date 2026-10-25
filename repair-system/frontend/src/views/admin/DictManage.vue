<template>
  <el-row :gutter="16">
    <!-- 报修类型 -->
    <el-col :span="10">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <b>报修类型</b>
            <el-button type="primary" size="small" @click="openType(null)">新增类型</el-button>
          </div>
        </template>
        <el-table :data="types">
          <el-table-column prop="typeName" label="名称" />
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="派单方式" width="140">
            <template #default="{ row }">
              <el-switch v-model="row.autoDispatch" size="small"
                         inline-prompt active-text="自动" inactive-text="人工"
                         @change="toggleDispatch(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="openType(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="removeType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>

    <!-- 位置 -->
    <el-col :span="14">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <b>位置(楼栋/单元/房间)</b>
            <el-button type="primary" size="small" @click="openLocation(null)">新增位置</el-button>
          </div>
        </template>
        <el-table :data="locations">
          <el-table-column prop="building" label="楼栋" />
          <el-table-column prop="unit" label="单元" />
          <el-table-column prop="room" label="房间" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="openLocation(row)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="removeLocation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
  </el-row>

  <!-- 类型编辑 -->
  <el-dialog v-model="typeVisible" :title="typeForm.id ? '编辑类型' : '新增类型'" width="420px">
    <el-form :model="typeForm" label-width="80px">
      <el-form-item label="名称"><el-input v-model="typeForm.typeName" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="typeForm.sort" :min="0" /></el-form-item>
      <el-form-item label="派单方式">
        <el-radio-group v-model="typeForm.autoDispatch">
          <el-radio :value="true">系统自动派单</el-radio>
          <el-radio :value="false">人工派单</el-radio>
        </el-radio-group>
      </el-form-item>
      <div class="form-tip">选「人工派单」后，该类型工单不再自动派给师傅，需管理员手动派单（如电梯故障需联系维保单位）。</div>
    </el-form>
    <template #footer>
      <el-button @click="typeVisible = false">取消</el-button>
      <el-button type="primary" @click="submitType">保存</el-button>
    </template>
  </el-dialog>

  <!-- 位置编辑 -->
  <el-dialog v-model="locationVisible" :title="locationForm.id ? '编辑位置' : '新增位置'" width="400px">
    <el-form :model="locationForm" label-width="60px">
      <el-form-item label="楼栋"><el-input v-model="locationForm.building" placeholder="如:3号楼" /></el-form-item>
      <el-form-item label="单元"><el-input v-model="locationForm.unit" placeholder="如:2单元" /></el-form-item>
      <el-form-item label="房间"><el-input v-model="locationForm.room" placeholder="如:205" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="locationVisible = false">取消</el-button>
      <el-button type="primary" @click="submitLocation">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTypes, addType, updateType, deleteType, getLocations, addLocation, updateLocation, deleteLocation } from '../../api/user'

const types = ref([])
const locations = ref([])
const typeVisible = ref(false)
const locationVisible = ref(false)
const typeForm = reactive({ id: null, typeName: '', sort: 0, autoDispatch: true })
const locationForm = reactive({ id: null, building: '', unit: '', room: '' })

async function load() {
  types.value = await getTypes()
  locations.value = await getLocations()
}

function openType(row) {
  typeForm.id = row?.id ?? null
  typeForm.typeName = row?.typeName ?? ''
  typeForm.sort = row?.sort ?? 0
  typeForm.autoDispatch = row?.autoDispatch ?? true
  typeVisible.value = true
}

/** 表格内直接切换派单方式: 保存后即时对新提交的工单生效 */
async function toggleDispatch(row) {
  try {
    await updateType({ id: row.id, typeName: row.typeName, sort: row.sort, autoDispatch: row.autoDispatch })
    ElMessage.success(`「${row.typeName}」已改为${row.autoDispatch ? '系统自动派单' : '人工派单'}`)
  } catch (e) {
    row.autoDispatch = !row.autoDispatch   // 失败回滚开关状态
  }
}

async function submitType() {
  if (!typeForm.typeName.trim()) return ElMessage.warning('请输入名称')
  if (typeForm.id) await updateType(typeForm)
  else await addType(typeForm)
  ElMessage.success('保存成功')
  typeVisible.value = false
  load()
}

async function removeType(row) {
  await ElMessageBox.confirm(`确定删除类型「${row.typeName}」吗?`, '提示', { type: 'warning' })
  await deleteType(row.id)
  load()
}

function openLocation(row) {
  locationForm.id = row?.id ?? null
  locationForm.building = row?.building ?? ''
  locationForm.unit = row?.unit ?? ''
  locationForm.room = row?.room ?? ''
  locationVisible.value = true
}

async function submitLocation() {
  if (!locationForm.building.trim() || !locationForm.room.trim()) return ElMessage.warning('请填写完整')
  if (locationForm.id) await updateLocation(locationForm)
  else await addLocation(locationForm)
  ElMessage.success('保存成功')
  locationVisible.value = false
  load()
}

async function removeLocation(row) {
  await ElMessageBox.confirm(`确定删除位置「${row.building} ${row.unit} ${row.room}」吗?`, '提示', { type: 'warning' })
  await deleteLocation(row.id)
  load()
}

onMounted(load)
</script>

<style scoped>
.form-tip { font-size: 12px; color: #909399; line-height: 1.6; padding-left: 80px; }
</style>
