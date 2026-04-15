<template>
  <el-drawer
    v-model="drawerVisible"
    direction="rtl"
    size="520px"
    class="info-detail-drawer"
    append-to-body
    :with-header="false"
    @closed="emit('closed')"
  >
    <div class="detail-drawer">
      <div class="detail-drawer__header">
        <div class="detail-drawer__title-row">
          <h3 class="detail-drawer__title">详情信息</h3>
          <el-tag class="detail-status-tag" :type="getStatusType()" effect="dark">
            {{ getStatusText() }}
          </el-tag>
        </div>
        <p class="detail-drawer__id">编号：{{ formData.id || '--' }}</p>
      </div>

      <transition name="drawer-content-fade" appear>
        <div v-if="drawerVisible" class="detail-drawer__body">
          <el-card class="detail-card" shadow="hover">
            <template #header>
              <div class="detail-card__header">基础信息</div>
            </template>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">名称</span>
                <span class="detail-value">{{ formData.name || '--' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">类型</span>
                <span class="detail-value">{{ getInfoTypeLabel(formData.type) }}</span>
              </div>
              <div class="detail-item" v-if="formData.targetType">
                <span class="detail-label">目标</span>
                <span class="detail-value">{{ formData.targetType }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">地点</span>
                <span class="detail-value">{{ formData.location || '--' }}</span>
              </div>
            </div>
          </el-card>

          <el-card class="detail-card" shadow="hover">
            <template #header>
              <div class="detail-card__header">时间与人员</div>
            </template>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">时间</span>
                <span class="detail-value">{{ formatStartTime(formData.startTime) || '--' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">创建时间</span>
                <span class="detail-value">{{ formData.createTime || '--' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">创建人</span>
                <span class="detail-value">{{ formData.createBy || '暂无创建人' }}</span>
              </div>
            </div>
          </el-card>

          <el-card class="detail-card" shadow="hover">
            <template #header>
              <div class="detail-card__header">附加信息</div>
            </template>
            <div class="detail-stack">
              <div class="detail-item detail-item--column">
                <span class="detail-label">路径</span>
                <span class="detail-value">{{ formData.fullPath || '--' }}</span>
              </div>
              <div class="detail-item detail-item--column">
                <span class="detail-label">内容描述</span>
                <span class="detail-value">{{ formData.contentDesc || '--' }}</span>
              </div>
            </div>
          </el-card>
        </div>
      </transition>

      <div class="detail-drawer__footer">
        <el-button @click="drawerVisible = false">关 闭</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  formData: { type: Object, required: true },
  getStatusType: { type: Function, required: true },
  getStatusText: { type: Function, required: true },
  getInfoTypeLabel: { type: Function, required: true },
  formatStartTime: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'closed'])

const drawerVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
</script>

<style scoped>
.info-detail-drawer :deep(.el-drawer__body) { padding: 0; }
.detail-drawer { height: 100%; display: flex; flex-direction: column; background: #f4f7fb; }
.detail-drawer__header { padding: 20px 22px 16px; border-bottom: 1px solid #e8edf5; background: #fff; }
.detail-drawer__title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.detail-drawer__title { margin: 0; font-size: 18px; font-weight: 600; color: #2f3a4a; }
.detail-drawer__id { margin: 10px 0 0; color: #5c6778; font-size: 14px; }
.detail-drawer__body { flex: 1; min-height: 0; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 20px; }
.detail-card { border: 1px solid #e5ebf3; border-radius: 10px; }
.detail-card :deep(.el-card__header) { padding: 12px 16px; border-bottom: 1px solid #edf1f6; }
.detail-card :deep(.el-card__body) { padding: 16px; }
.detail-card__header { font-size: 14px; font-weight: 600; color: #3d4a5d; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 16px; }
.detail-stack { display: flex; flex-direction: column; gap: 14px; }
.detail-item { display: flex; align-items: flex-start; gap: 10px; line-height: 1.6; }
.detail-item--column { flex-direction: column; gap: 4px; }
.detail-label { min-width: 72px; color: #8a94a6; font-size: 13px; }
.detail-value { color: #2f3a4a; font-size: 14px; word-break: break-all; }
.detail-drawer__footer { padding: 14px 20px; border-top: 1px solid #e8edf5; background: #fff; display: flex; justify-content: flex-end; }
.drawer-content-fade-enter-active, .drawer-content-fade-leave-active { transition: opacity 0.22s ease, transform 0.22s ease; }
.drawer-content-fade-enter-from, .drawer-content-fade-leave-to { opacity: 0; transform: translateX(10px); }
</style>
