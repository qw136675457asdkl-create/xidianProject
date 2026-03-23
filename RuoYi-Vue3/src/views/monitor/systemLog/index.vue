<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <span>系统日志文件列表</span>
      </template>

      <el-table v-loading="loading" :data="fileList" border>
        <el-table-column label="序号" type="index" width="80" />

        <el-table-column label="文件名">
          <template #default="{ row }">
            <el-link type="primary" @click="handlePreview(row)">
              {{ getFileName(row) }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePreview(row)" v-hasPermi="['monitor:systemLog:preview']">
              预览
            </el-button>
            <el-button link type="info" @click="handleDownload(row)" v-hasPermi="['monitor:systemLog:download']">
              下载
            </el-button>
          </template>
        </el-table-column>

      </el-table>
    </el-card>

    <el-dialog v-model="previewOpen" title="文件预览" width="70%">
      <pre v-if="previewContent" class="preview-box">{{ previewContent }}</pre>
      <div v-else>暂无内容</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listSysLogs, previewLog, downloadLog } from '@/api/monitor/sysLog'

const loading = ref(false)
const fileList = ref([])
const previewOpen = ref(false)
const previewContent = ref('')

onMounted(() => {
  getList()
})

function getList() {
  loading.value = true
  listSysLogs()
    .then((res) => {
      fileList.value = res.rows || []
    })
    .catch(() => {
      ElMessage.error('获取文件列表失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function getFileName(row) {
  return row.fileName || row.name || row
}

function formatPreviewLine(line) {
  if (line === undefined || line === null) {
    return ''
  }
  if (typeof line === 'object') {
    return JSON.stringify(line, null, 2)
  }
  return String(line)
}

function formatPreviewContent(rows) {
  if (Array.isArray(rows)) {
    return rows.map((line) => formatPreviewLine(line)).join('\n')
  }
  return formatPreviewLine(rows)
}

function handlePreview(row) {
  const fileName = getFileName(row)
  previewLog(fileName)
    .then((res) => {
      previewContent.value = formatPreviewContent(res.data?.rows)
      previewOpen.value = true
    })
    .catch(() => {
      ElMessage.error('预览失败')
    })
}
function handleDownload(row) {
  const fileName = getFileName(row)
  downloadLog(fileName)
    .then((res) => {
      const url = URL.createObjectURL(new Blob([res]))
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    })
    .catch(() => {
      ElMessage.error('下载失败')
    })
}
</script>

<style scoped>
.preview-box {
  max-height: 600px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
}
</style>
