<template>
  <el-dialog
    title="新增项目"
    v-model="dialogVisible"
    width="560px"
    class="ant-form-dialog"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    append-to-body
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="84px" class="ant-form-layout">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formModel.name" placeholder="请输入项目名称" />
      </el-form-item>
      <el-form-item label="内容描述" prop="contentDesc">
        <el-input
          v-model="formModel.contentDesc"
          type="textarea"
          :maxlength="200"
          show-word-limit
          :autosize="{ minRows: 4, maxRows: 6 }"
          placeholder="请输入内容"
        />
      </el-form-item>
      <el-form-item label="路径" prop="path">
        <el-input v-model="formModel.path" placeholder="系统自动生成路径" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" class="ant-confirm-btn" :loading="loading" @click="handleSubmit">确 定</el-button>
        <el-button class="ant-cancel-btn" :disabled="loading" @click="dialogVisible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  formModel: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed'])
const formRef = ref(null)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

function handleSubmit() {
  if (props.loading) return
  formRef.value?.validate(valid => {
    if (valid) emit('submit')
  })
}

function handleClosed() {
  formRef.value?.clearValidate()
  emit('closed')
}
</script>

<style scoped>
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
.ant-confirm-btn, .ant-cancel-btn { border-radius: 8px; }
</style>
