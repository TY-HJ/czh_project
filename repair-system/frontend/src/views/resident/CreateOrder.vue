<template>
  <el-card>
    <template #header><b>提交报修</b></template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 640px">
      <el-form-item label="报修类型" prop="typeId">
        <el-select v-model="form.typeId" placeholder="请选择类型" style="width: 100%">
          <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="位置" prop="locationId">
        <el-select v-model="form.locationId" placeholder="请选择楼栋/房间" style="width: 100%">
          <el-option v-for="l in locations" :key="l.id"
                     :label="`${l.building} ${l.unit} ${l.room}`" :value="l.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="一句话描述问题,如:卫生间水龙头漏水" />
      </el-form-item>
      <el-form-item label="详细描述">
        <el-input v-model="form.description" type="textarea" :rows="4"
                  placeholder="补充说明,如:什么时候开始的、影响了什么" />
      </el-form-item>
      <el-form-item label="紧急程度">
        <el-radio-group v-model="form.priority">
          <el-radio value="NORMAL">普通</el-radio>
          <el-radio value="URGENT">紧急</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="现场照片">
        <el-upload v-model:file-list="fileList" :action="uploadUrl" :headers="uploadHeaders"
                   list-type="picture-card"
                   :on-success="onUploadSuccess" :on-error="onUploadError" :before-upload="beforeUpload"
                   :limit="4" accept="image/*">
          <el-icon><Plus /></el-icon>
        </el-upload>
        <div style="color: #999; font-size: 12px">最多4张,每张不超过10MB,有助于师傅提前判断问题</div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">提交报修</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, uploadUrl } from '../../api/order'
import { getTypes, getLocations } from '../../api/user'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const types = ref([])
const locations = ref([])
const fileList = ref([])

const form = reactive({
  typeId: null, locationId: null, title: '',
  description: '', priority: 'NORMAL', images: ''
})

// el-upload 直传不走 axios 拦截器,必须手动携带 JWT,否则后端 401
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + localStorage.getItem('token') }))
const rules = {
  typeId: [{ required: true, message: '请选择报修类型', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择位置', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

onMounted(async () => {
  types.value = await getTypes()
  locations.value = await getLocations()
})

function beforeUpload(file) {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过10MB')
    return false
  }
  return true
}

// 上传成功后 el-upload 会把响应对象当结果,取其中的 data(URL)
function onUploadSuccess(res) {
  const url = res.data
  form.images = form.images ? form.images + ',' + url : url
}

function onUploadError() {
  ElMessage.error('图片上传失败,请重试')
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    // 后端返回提交结果: 若已智能派单, 直接告知住户维修去向, 避免"等待管理员派单"的误导
    const vo = await createOrder(form)
    if (vo && vo.status === 1 && vo.workerName) {
      ElMessage.success(`提交成功(工单号 ${vo.orderNo}),已自动派单给 ${vo.workerName},请留意消息通知`)
    } else if (vo && vo.dispatchHint) {
      // 未自动派单: 把系统给出的原因如实告知, 避免住户误以为没人管
      ElMessage.success(`提交成功(工单号 ${vo.orderNo}),本次转人工派单:${vo.dispatchHint},管理员会尽快安排师傅`)
    } else {
      ElMessage.success(`提交成功(工单号 ${vo && vo.orderNo ? vo.orderNo : ''}),已通知管理员尽快派单`)
    }
    router.push('/my-orders')
  } finally {
    loading.value = false
  }
}
</script>
