<template>
  <div class="upload-draft-panel">
    <el-alert :title="alertTitle" type="info" :closable="false" show-icon class="experiment-upload__alert" />
    <el-upload
      ref="uploadRef"
      drag
      action=""
      :auto-upload="false"
      :show-file-list="false"
      multiple
      :disabled="disabled"
      :accept="accept"
      class="experiment-upload"
      :on-change="file => emit('draft-change', file)"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">{{ dropText }}<em>点击选择文件</em></div>
      <template #tip>
        <div class="experiment-upload__tip">{{ tipText }}</div>
      </template>
    </el-upload>
    <input
      ref="folderInputRef"
      class="experiment-upload__folder-input"
      type="file"
      webkitdirectory
      multiple
      :disabled="disabled"
      :accept="accept"
      @change="event => emit('folder-change', event)"
    />
    <div class="experiment-upload__meta">
      <span class="experiment-upload__count">{{ countText }}</span>
      <div class="experiment-upload__actions">
        <el-button link type="primary" :disabled="disabled" @click="openFolderPicker">
          <el-icon><ElIconFolder /></el-icon>
          <span>选择文件夹</span>
        </el-button>
        <el-button link type="primary" :disabled="!draftFiles.length || disabled" @click="clearFiles">清空文件</el-button>
      </div>
    </div>
    <div v-if="progress.visible" class="experiment-upload__progress">
      <div class="experiment-upload__progress-text">{{ progress.text }}</div>
      <el-progress :percentage="progress.percentage" :status="progress.status" :stroke-width="10" />
    </div>
    <div v-if="draftFiles.length" class="experiment-upload__list">
      <div v-for="file in draftFiles" :key="file.uid" class="experiment-upload__list-item">
        <div class="experiment-upload__list-main">
          <el-icon class="experiment-upload__list-icon"><ElIconDocument /></el-icon>
          <span class="experiment-upload__list-name" :title="file.name">{{ file.name }}</span>
        </div>
        <div class="experiment-upload__list-side">
          <span class="experiment-upload__list-size">{{ formatSize(file.size) }}</span>
          <el-button link type="danger" :disabled="disabled" @click="emit('remove-file', file.uid)">移除</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled, Folder as ElIconFolder, Document as ElIconDocument } from '@element-plus/icons-vue'

defineProps({
  disabled: {
    type: Boolean,
    default: false
  },
  accept: {
    type: String,
    default: ''
  },
  draftFiles: {
    type: Array,
    default: () => []
  },
  progress: {
    type: Object,
    default: () => ({})
  },
  alertTitle: {
    type: String,
    default: ''
  },
  dropText: {
    type: String,
    default: '拖拽文件到此处，或'
  },
  tipText: {
    type: String,
    default: ''
  },
  countText: {
    type: String,
    default: ''
  },
  formatSize: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['draft-change', 'folder-change', 'clear-files', 'remove-file'])

const uploadRef = ref(null)
const folderInputRef = ref(null)

function openFolderPicker() {
  folderInputRef.value?.click()
}

function clearFiles() {
  uploadRef.value?.clearFiles()
  if (folderInputRef.value) {
    folderInputRef.value.value = ''
  }
  emit('clear-files')
}

function resetPanel() {
  uploadRef.value?.clearFiles()
  if (folderInputRef.value) {
    folderInputRef.value.value = ''
  }
}

defineExpose({
  clearFiles: resetPanel,
  openFolderPicker,
  resetPanel
})
</script>

<style scoped>
.experiment-upload__alert {
  margin-bottom: 14px;
}

.experiment-upload {
  width: 100%;
}

.experiment-upload :deep(.el-upload) {
  width: 100%;
}

.experiment-upload :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 14px;
  border-color: #cbd5e1;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.experiment-upload__tip {
  margin-top: 8px;
  color: #667085;
  line-height: 1.6;
}

.experiment-upload__folder-input {
  display: none;
}

.experiment-upload__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.experiment-upload__count {
  color: #667085;
  font-size: 13px;
}

.experiment-upload__actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.experiment-upload__progress {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid rgba(203, 213, 225, 0.9);
}

.experiment-upload__progress-text {
  margin-bottom: 10px;
  color: #475467;
  font-size: 13px;
}

.experiment-upload__list {
  margin-top: 14px;
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

.experiment-upload__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(248, 250, 252, 0.96);
}

.experiment-upload__list-item + .experiment-upload__list-item {
  margin-top: 10px;
}

.experiment-upload__list-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.experiment-upload__list-icon {
  flex-shrink: 0;
  color: #2563eb;
}

.experiment-upload__list-name {
  min-width: 0;
  color: #1f2937;
  font-size: 13px;
  word-break: break-all;
}

.experiment-upload__list-side {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.experiment-upload__list-size {
  color: #667085;
  font-size: 12px;
}
</style>
