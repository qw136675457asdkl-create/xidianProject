<template>
  <div class="app-container">
    <el-row :gutter="15">
      <el-form ref="formRef" :model="formData" :rules="rules" size="default" label-width="100px" v-hasPermi="['system:layerConfiguration:query']">
        <el-col :span="8">
          <el-form-item label-width="120px" label="图层默认透明度" prop="field103">
            <el-input v-model="formData.field103" type="text" placeholder="请输入图层默认透明度" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-row gutter="15">
            <el-col :span="8">
              <el-form-item label-width="120px" label="默认背景颜色" prop="field105">
                <!-- <el-radio-group v-model="formData.field105" size="default">
                  <el-radio v-for="(item, index) in field105Options" :key="index" :value="item.value"
                    :disabled="item.disabled">{{item.label}}</el-radio>
                </el-radio-group> -->
                <el-checkbox-group v-model="formData.field105" size="default">
                  <el-checkbox v-for="(item, index) in field105Options" :key="index" :label="item.label"
                    :value="item.value" :disabled="item.disabled" />
                </el-checkbox-group>
              </el-form-item>
            </el-col>
            <el-col :span="10">
              <el-form-item label-width="240px" label="图层背景色（不启用透明时生效）" prop="field106" required>
                <el-color-picker v-model="formData.field106" size="default"></el-color-picker>
              </el-form-item>
            </el-col>
          </el-row>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="120px" label="全局文字颜色" prop="field107" required>
            <el-color-picker v-model="formData.field107" size="default"></el-color-picker>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label-width="120px" label="全局文字大小" prop="field108">
            <el-input v-model="formData.field108" type="text" placeholder="请输入全局文字大小" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="120px" label="图层启用状态" prop="field109">
            <el-checkbox-group v-model="formData.field109" size="default">
              <el-checkbox v-for="(item, index) in field109Options" :key="index" :label="item.label"
                :value="item.value" :disabled="item.disabled" />
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label-width="120px" label="图层刷新频率" prop="field110">
            <el-input v-model="formData.field110" type="text" placeholder="请输入图层刷新频率" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="120px" label="图层悬浮提示" prop="field111">
            <!-- <el-radio-group v-model="formData.field111" size="default">
              <el-radio v-for="(item, index) in field111Options" :key="index" :value="item.value"
                :disabled="item.disabled">{{item.label}}</el-radio>
            </el-radio-group> -->
            <el-checkbox-group v-model="formData.field111" size="default">
              <el-checkbox v-for="(item, index) in field111Options" :key="index" :label="item.label"
                :value="item.value" :disabled="item.disabled" />
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="120px" label="图层异常提示" prop="field112">
            <!-- <el-radio-group v-model="formData.field112" size="default">
              <el-radio v-for="(item, index) in field112Options" :key="index" :value="item.value"
                :disabled="item.disabled">{{item.label}}</el-radio>
            </el-radio-group> -->
            <el-checkbox-group v-model="formData.field112" size="default">
              <el-checkbox v-for="(item, index) in field112Options" :key="index" :label="item.label"
                :value="item.value" :disabled="item.disabled" />
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label-width="120px" label="图层数据缓存" prop="field113">
            <!-- <el-radio-group v-model="formData.field113" size="default">
              <el-radio v-for="(item, index) in field113Options" :key="index" :value="item.value"
                :disabled="item.disabled">{{item.label}}</el-radio>
            </el-radio-group> -->
            <el-checkbox-group v-model="formData.field113" size="default">
              <el-checkbox v-for="(item, index) in field113Options" :key="index" :label="item.label"
                :value="item.value" :disabled="item.disabled" />
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label-width="120px" label="缓存过期时间" prop="field114">
            <el-input v-model="formData.field114" type="text" placeholder="请输入缓存过期时间" clearable
              :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="24" >
          <el-form-item>
            <el-button type="primary" @click="submitForm" v-hasPermi="['system:layerConfiguration:save']">保存全局配置</el-button>
            <el-button @click="resetForm" v-hasPermi="['system:layerConfiguration:reset']">恢复默认配置</el-button>
          </el-form-item>
        </el-col>
      </el-form>
    </el-row>
  </div>
</template>
<script setup>
import { ref, reactive, toRefs, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveLayerConfiguration, getLayerConfiguration, resetLayerConfiguration } from '@/api/layerConfiguration'

const formRef = ref()
const loading = ref(false)

const data = reactive({
  formData: {
    field103: '90',
    field105: ['使用透明背景'],
    field106: '#00eeff',
    field107: '#000000',
    field108: '12',
    field109: ['启用所有功能图层', '飞机雷达图图层', '仪表盘图层', 'ADS-B应答数据面板图层', '导航图层'],
    field110: '10',
    field111: ['启用图层悬浮显示名称及状态提示'],
    field112: ['图层加载失败时显示弹窗提示'],
    field113: ['启用图层数据本地缓存（提升重复加载速度）'],
    field114: '30'
  },
  rules: {
    field103: [{
      required: true,
      message: '请输入图层默认透明度',
      trigger: 'blur'
    }],
    field108: [{
      required: true,
      message: '请输入全局文字大小',
      trigger: 'blur'
    }],
    field109: [{
      required: true,
      type: 'array',
      message: '请至少选择一个图层启用状态',
      trigger: 'change'
    }],
    field110: [{
      required: true,
      message: '请输入图层刷新频率',
      trigger: 'blur'
    }],
    field114: [{
      required: true,
      message: '请输入缓存过期时间',
      trigger: 'blur'
    }]
  }
})

const { formData, rules } = toRefs(data)
const field105Options = ref([{
  "label": "使用透明背景",
  "value": "使用透明背景"
}])
const field109Options = ref([{
  "label": "启用所有功能图层",
  "value": "启用所有功能图层"
}, {
  "label": "飞机雷达图图层",
  "value": "飞机雷达图图层"
}, {
  "label": "仪表盘图层",
  "value": "仪表盘图层"
}, {
  "label": "ADS-B应答数据面板图层",
  "value": "ADS-B应答数据面板图层"
}, {
  "label": "导航图层",
  "value": "导航图层"
}])
const field111Options = ref([{
  "label": "启用图层悬浮显示名称及状态提示",
  "value": "启用图层悬浮显示名称及状态提示"
}])
const field112Options = ref([{
  "label": "图层加载失败时显示弹窗提示",
  "value": "图层加载失败时显示弹窗提示"
}])
const field113Options = ref([{
  "label": "启用图层数据本地缓存（提升重复加载速度）",
  "value": "启用图层数据本地缓存（提升重复加载速度）"
}])
/**
 * @name: 表单提交
 * @description: 表单提交方法
 */
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    loading.value = true
    saveLayerConfiguration(formData.value)
      .then(() => {
        ElMessage.success('保存成功')
      })
      .catch((error) => {
        console.error('保存失败:', error)
        ElMessage.error('保存失败，请稍后重试')
      })
      .finally(() => {
        loading.value = false
      })
  })
}

/**
 * @name: 表单重置
 * @description: 恢复为后端默认配置
 */
function resetForm() {
  ElMessageBox.confirm('确定要恢复默认配置吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      loading.value = true
      resetLayerConfiguration()
        .then(() => {
          ElMessage.success('已恢复默认配置')
          loadConfiguration()
        })
        .catch((error) => {
          console.error('重置失败:', error)
          ElMessage.error('重置失败，请稍后重试')
        })
        .finally(() => {
          loading.value = false
        })
    })
    .catch(() => {
      // 用户取消，不做处理
    })
}

/**
 * @name: 加载配置
 * @description: 从后端加载当前图层配置
 */
function loadConfiguration() {
  loading.value = true
  getLayerConfiguration()
    .then((res) => {
      // ruoyi 后端返回 { code, msg, data }
      const cfg = res && res.data ? res.data : res
      if (cfg) {
        Object.assign(formData.value, cfg)
      }
    })
    .catch((error) => {
      console.error('加载配置失败:', error)
      ElMessage.error('加载配置失败，请稍后重试')
    })
    .finally(() => {
      loading.value = false
    })
}

// 进入页面时自动从后端拉取一次配置
onMounted(() => {
  loadConfiguration()
})

</script>
<style>
  .el-form-item {
    margin-bottom: 35px;
  }
</style>
