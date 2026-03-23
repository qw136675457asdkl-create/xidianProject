<template>
    <div class="app-container">
                <div>
                    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="88px">
                        <el-form-item label="任务名称" prop="taskName">
                            <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable style="width: 200px" />
                        </el-form-item>
                        <el-form-item label="所属项目" prop="projectId">
                            <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable style="width: 200px">
                                <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="所属试验" prop="experimentId">
                            <el-select v-model="queryParams.experimentId" placeholder="请选择所属试验" clearable style="width: 200px">
                                <el-option v-for="item in experimentOptions" :key="item.experimentId" :label="item.experimentName" :value="item.experimentId" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="创建人" prop="createBy">
                            <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable style="width: 200px" />
                        </el-form-item>
                    </el-form>
                    <el-row :gutter="10" v-show="showSearch" style="margin-bottom: 10px;">
                        <el-col :span="24">
                            <div style="display: flex; justify-content: flex-start; gap: 10px;">
                                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                            </div>
                        </el-col>
                    </el-row>
                  <el-row :gutter="10" class="mb8">
                    <el-col :span="1.5">
                      <el-button
                          type="primary"
                          plain
                          icon="Plus"
                          @click="handleAddSimulationData"
                      >添加仿真任务</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="success"
                          plain
                          icon="DocumentAdd"
                          @click="handleAdd"
                          v-hasPermi="['data:info:add']"
                      >添加项目信息</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="info"
                          plain
                          icon="FolderAdd"
                          @click="handleAddExperiment"
                          v-hasPermi="['data:info:add']"
                      >添加试验信息</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="danger"
                          plain
                          icon="Delete"
                          :disabled="multiple"
                          @click="handleDelete"
                      >删除</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="warning"
                          plain
                          icon="Refresh"
                          @click="getList"
                      >刷新</el-button>
                    </el-col>

                    <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
                  </el-row>

                    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
                        <el-table-column type="selection" width="55" align="center" />
                        <el-table-column label="任务ID" align="center" prop="id" />
                        <el-table-column label="任务名称" align="center" prop="taskName" :show-overflow-tooltip="true" />
                        
                        
                        <el-table-column label="所属项目" align="center" prop="projectName" :show-overflow-tooltip="true" />
                        <el-table-column label="所属试验" align="center" prop="experimentName" :show-overflow-tooltip="true" />
                        <el-table-column label="路径" align="center" prop="dataFilePath" :show-overflow-tooltip="true" />
                        <el-table-column label="数据种类" align="center" prop="dataType" :show-overflow-tooltip="true" />
                        <el-table-column label="试验目标" align="center" prop="targetType" :show-overflow-tooltip="true" />
                        <el-table-column label="创建人" align="center" prop="createBy" />
                        <el-table-column label="创建时间" align="center" prop="createTime" >
                            <template #default="scope">
                                <span>{{ parseTime(scope.row.createTime) }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="状态" align="center" prop="status" >
                            <template #default="scope">
                                <span v-if="scope.row.status === 1">已完成</span>
                                <span v-else-if="scope.row.status === 2">生成中</span>
                                <span v-else-if="scope.row.status === 3">部分失败</span>
                                <span v-else-if="scope.row.status === 4">部分成功</span>
                                <span v-else>失败</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
                            <template #default="scope">
                            <el-tooltip content="详情" placement="top">
                                <el-button link type="primary" icon="View" @click="handleView(scope.row)"></el-button>
                            </el-tooltip>
                            <el-tooltip content="删除" placement="top">
                                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
                            </el-tooltip>
                            </template>
                        </el-table-column>
                    </el-table>
                    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
                </div>

        <el-dialog
          v-model="simulationDialogOpen"
          title="添加仿真任务"
          width="1380px"
          top="3vh"
          append-to-body
          class="simulation-dialog"
        >
          <div class="simulation-panel">
            <div class="simulation-block">
              <div class="simulation-block__title">基础配置</div>
              <div class="simulation-block__body">
                <div class="simulation-grid simulation-grid--three">
                  <div class="simulation-field">
                    <label class="simulation-field__label">任务名称:</label>
                    <el-input v-model="simulationForm.taskName" placeholder="请输入" />
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">所属项目:</label>
                    <el-select
                      v-model="simulationForm.projectId"
                      placeholder="请选择所属项目"
                      clearable
                      @change="handleSimulationProjectChange"
                    >
                      <el-option
                        v-for="item in projectOptions"
                        :key="item.projectId"
                        :label="item.projectName"
                        :value="item.projectId"
                      />
                    </el-select>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">所属试验:</label>
                    <el-select v-model="simulationForm.experimentId" placeholder="请选择所属试验" clearable>
                      <el-option
                        v-for="item in filteredSimulationExperiments"
                        :key="item.experimentId"
                        :label="item.experimentName"
                        :value="item.experimentId"
                      />
                    </el-select>
                  </div>
                </div>
              </div>
            </div>

            <div class="simulation-block">
              <div class="simulation-block__title">载机模型</div>
              <div class="simulation-block__body">
                <div class="simulation-grid simulation-grid--three simulation-grid--aircraft">
                  <div class="simulation-field">
                    <label class="simulation-field__label">载机类型:</label>
                    <el-select v-model="simulationForm.aircraftType" placeholder="请选择载机类型">
                      <el-option
                        v-for="item in simulationAircraftTypeOptions"
                        :key="item"
                        :label="item"
                        :value="item"
                      />
                    </el-select>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">起点坐标:</label>
                    <div class="triple-inputs">
                      <el-input v-model="simulationForm.startCoordinate.lng" placeholder="经" />
                      <el-input v-model="simulationForm.startCoordinate.lat" placeholder="纬" />
                      <el-input v-model="simulationForm.startCoordinate.alt" placeholder="高" />
                    </div>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">起点速度:</label>
                    <div class="triple-inputs">
                      <el-input v-model="simulationForm.startVelocity.lng" placeholder="经" />
                      <el-input v-model="simulationForm.startVelocity.lat" placeholder="纬" />
                      <el-input v-model="simulationForm.startVelocity.alt" placeholder="高" />
                    </div>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">运动模型:</label>
                    <el-select v-model="simulationForm.motionModel" placeholder="请选择运动模型">
                      <el-option
                        v-for="item in simulationMotionModelOptions"
                        :key="item"
                        :label="item"
                        :value="item"
                      />
                    </el-select>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">终点坐标:</label>
                    <div class="triple-inputs">
                      <el-input v-model="simulationForm.endCoordinate.lng" placeholder="经" />
                      <el-input v-model="simulationForm.endCoordinate.lat" placeholder="纬" />
                      <el-input v-model="simulationForm.endCoordinate.alt" placeholder="高" />
                    </div>
                  </div>
                  <div class="simulation-field">
                    <label class="simulation-field__label">起点姿态:</label>
                    <div class="triple-inputs">
                      <el-input v-model="simulationForm.startAttitude.roll" placeholder="横滚" />
                      <el-input v-model="simulationForm.startAttitude.heading" placeholder="偏航" />
                      <el-input v-model="simulationForm.startAttitude.pitch" placeholder="俯仰" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="simulation-block simulation-block--data">
              <div class="simulation-block__title">数据参数</div>
              <div class="simulation-block__body">
                <el-tabs v-model="activeSimulationTab" class="simulation-tabs">
                  <el-tab-pane
                    v-for="tab in simulationTabs"
                    :key="tab.name"
                    :name="tab.name"
                    :label="tab.label"
                  />
                </el-tabs>

                <div class="simulation-data-panel">
                  <div class="simulation-grid simulation-grid--single">
                    <div class="simulation-field simulation-field--wide">
                      <label class="simulation-field__label">数据名称:</label>
                      <el-input
                        v-model="currentSimulationTabForm.dataName"
                        :placeholder="`请输入，自动添加前缀mock-${currentSimulationTab.label}`"
                      />
                    </div>
                    <div class="simulation-field simulation-field--wide">
                      <label class="simulation-field__label">输出类型:</label>
                      <el-radio-group v-model="currentSimulationTabForm.outputType">
                        <el-radio value="bit">bit</el-radio>
                        <el-radio value="csv">csv</el-radio>
                      </el-radio-group>
                    </div>
                    <div class="simulation-field simulation-field--wide">
                      <label class="simulation-field__label">时长范围:</label>
                      <el-date-picker
                        v-model="currentSimulationTabForm.timeRange"
                        type="datetimerange"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        range-separator="→"
                        start-placeholder="开始时间"
                        end-placeholder="结束时间"
                      />
                    </div>
                    <div class="simulation-field simulation-field--wide">
                      <label class="simulation-field__label">数据帧率:</label>
                      <div class="frame-rate-field">
                        <el-select v-model="currentSimulationTabForm.frameRate">
                          <el-option
                            v-for="item in simulationFrameRateOptions"
                            :key="item"
                            :label="item"
                            :value="item"
                          />
                        </el-select>
                        <span class="frame-rate-unit">Hz</span>
                      </div>
                    </div>
                  </div>

                  <div class="simulation-item-list">
                    <div
                      v-for="(item, index) in currentSimulationTabForm.items"
                      :key="`${activeSimulationTab}-${index}`"
                      class="simulation-item-row"
                    >
                      <div class="simulation-item-name">{{ item.name }}</div>
                      <div class="simulation-item-field">
                        <span class="simulation-item-field__label">推荐值:</span>
                        <el-input v-model="item.recommendedValue" />
                      </div>
                      <div class="simulation-item-field">
                        <span class="simulation-item-field__label">波动阈:</span>
                        <el-input v-model="item.threshold" />
                      </div>
                      <el-button
                        class="simulation-item-delete"
                        link
                        type="danger"
                        icon="Delete"
                        @click="removeSimulationDataItem(index)"
                      />
                    </div>
                  </div>

                  <div class="simulation-data-actions">
                    <el-button class="simulation-add-item-btn" @click="addSimulationDataItem">添加数据</el-button>
                  </div>

                  <div class="simulation-subpage-save">
                    <el-button class="simulation-save-btn" type="primary" @click="handleSaveSimulationPage">保存子页面</el-button>
                  </div>
                </div>
              </div>
            </div>

            <div class="simulation-footer">
              <el-button class="simulation-footer__reset" @click="handleResetSimulationForm">全部重置</el-button>
              <el-button class="simulation-footer__confirm" type="primary" @click="handleGenerateSimulation">确认生成</el-button>
            </div>
          </div>
        </el-dialog>

        <!-- 详情对话框 -->
        <el-dialog title="任务详情" v-model="openView" width="700px" append-to-body>
            <el-form :model="form" label-width="100px">
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="ID">{{ form.id }}</el-form-item>
                        <el-form-item label="任务名称">{{ form.taskName }}</el-form-item>
                        <el-form-item label="是否模拟">
                            <span v-if="form.isSimulation === true">真实数据</span>
                            <span v-else-if="form.isSimulation === false">模拟数据</span>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="所属试验">{{ form.experimentName }}</el-form-item>
                        <el-form-item label="所属项目">{{ form.projectName }}</el-form-item>
                        <el-form-item label="试验目标">{{ form.targetType }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="路径">{{ form.dataFilePath }}</el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验时间">{{ parseTime(form.startTime) }}</el-form-item>
                        <el-form-item label="试验地点">{{ form.location }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="内容描述">{{ form.contentDesc }}</el-form-item>
                        <el-form-item label="创建人">{{ form.createBy }}</el-form-item>
                        <el-form-item label="创建时间">{{ parseTime(form.createTime) }}</el-form-item>
                    </el-col>
                </el-row>
            </el-form>

            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="openView = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>
      <el-dialog :title="title" v-model="open" width="560px">
          <el-form ref="infoRef" :model="form" :rules="rules" label-width="84px" class="ant-form-layout">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入试验名称"/>
          </el-form-item>
          <el-form-item label="所属项目" prop="parentId" v-if="form.type === 'experiment'">
            <el-select v-model="form.parentId" placeholder="请选择所属项目" filterable clearable>
              <el-option
                v-for="item in projectOptions"
                :key="item.projectId"
                :label="item.projectName"
                :value="item.projectId"
              />
            </el-select>
          </el-form-item>
        <el-form-item label="试验目标" prop="targetId" v-if="form.type === 'experiment'">
            <el-select v-model="form.targetId" placeholder="请选择试验目标">
              <el-option
                v-for="item in targetTypeOptions"
                :key="item.targetId"
                :label="item.targetType"
                :value="item.targetId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="试验日期" prop="startTime" v-if="form.type === 'experiment'">
            <el-date-picker clearable
              v-model="form.startTime"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择开始日期">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="试验地点" prop="location" v-if="form.type === 'experiment'">
            <el-input v-model="form.location" placeholder="请输入地点" />
          </el-form-item>
          <el-form-item label="内容描述" prop="contentDesc">
            <el-input
              v-model="form.contentDesc"
              type="textarea"
              :maxlength="200"
              show-word-limit
              :autosize="{ minRows: 4, maxRows: 6 }"
              placeholder="请输入内容"
            />
          </el-form-item>
          <el-form-item label="创建人" prop="createBy" v-if="form.createBy">
            <el-input :model-value="form.createBy" disabled />
          </el-form-item>
          <el-form-item label="创建时间" prop="createTime" v-if="form.createTime">
            <el-date-picker
              v-model="form.createTime"
              type="datetime" 
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              readonly
              placeholder="无时间数据">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="路径" prop="path">
            <el-input
              v-model="form.path"
              :placeholder="isAdd ? '系统自动生成路径' : '请输入路径'"
              :disabled="isAdd"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button type="primary" class="ant-confirm-btn" :loading="submitLoading" @click="submitForm">确 定</el-button>
            <el-button class="ant-cancel-btn" :disabled="submitLoading" @click="cancel">取 消</el-button>
          </div>
        </template>
      </el-dialog>
    </div>
</template>
<script setup name="Business">
import useAppStore from '@/store/modules/app'
import {Splitpanes, Pane} from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import {getInfo, addInfo, updateInfo, getExperimentInfos} from "@/api/data/info"
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
const projectOptions = ref([])
const experimentOptions = ref([])
const targetTypeOptions = ref([])
const open = ref(false)
const submitLoading = ref(false)
const showSearch = ref(true)
const loading = ref(false)
const total = ref(0)
const taskList = ref([])
const multiple = ref(true)
const openView = ref(false)
const selectedTaskRows = ref([])
const simulationDialogOpen = ref(false)
const mockTaskSource = ref([])
const { proxy } = getCurrentInstance()

const isAdd = computed(() => {
  return title.value === '添加项目' || title.value === '添加试验';
});

const title = ref("")
const simulationTabs = [
  { name: 'carrier', label: '载机惯导数据' },
  { name: 'radar', label: '雷达航迹数据' },
  { name: 'data3', label: '数据 3' },
  { name: 'option4', label: '选项 4' },
  { name: 'electronic', label: '电子战' },
  { name: 'ais', label: 'AIS' },
  { name: 'adsb', label: 'ADS-B' },
  { name: 'communication', label: '通信数据' },
  { name: 'data9', label: '数据 9' },
  { name: 'data10', label: '数据 10' }
]
const simulationAircraftTypeOptions = ['侦察机', '运输机', '无人机', '预警机']
const simulationMotionModelOptions = ['直线模型', '盘旋模型', '折线模型', '机动模型']
const simulationFrameRateOptions = ['1', '2', '4', '8', '16', '32', '64']
const activeSimulationTab = ref(simulationTabs[0].name)
const fallbackProjectOptions = [
  { projectId: 'P001', projectName: '模拟项目A' },
  { projectId: 'P002', projectName: '模拟项目B' },
  { projectId: 'P003', projectName: '模拟项目C' }
]
const fallbackTargetTypeOptions = [
  { targetId: 'T001', targetType: '空中目标' },
  { targetId: 'T002', targetType: '海面目标' },
  { targetId: 'T003', targetType: '地面目标' }
]
const mockTaskPrefixes = ['侦测分析任务', '航迹推演任务', '态势感知任务', '联合模拟任务', '数据融合任务']
const mockDataTypes = ['载机惯导数据', '雷达航迹数据', 'AIS', 'ADS-B', '电子战', '通信数据']
const mockLocations = ['西安', '青岛', '三亚', '舟山', '连云港', '湛江']
const mockCreators = ['admin', 'analyst', 'zhangsan', 'lisi', 'wangwu']
const mockStatusOptions = [1, 2, 3, 4, 5]

function createSimulationDataItem(index) {
  return {
    name: `数据${index}`,
    recommendedValue: '0',
    threshold: '0%'
  }
}

function createSimulationTabState(label) {
  return {
    dataName: `mock-${label}`,
    outputType: 'bit',
    timeRange: [],
    frameRate: '8',
    items: [createSimulationDataItem(1), createSimulationDataItem(2), createSimulationDataItem(3)]
  }
}

function createSimulationForm() {
  const tabs = {}
  simulationTabs.forEach(tab => {
    tabs[tab.name] = createSimulationTabState(tab.label)
  })

  return {
    taskName: '',
    projectId: null,
    experimentId: null,
    aircraftType: simulationAircraftTypeOptions[0],
    motionModel: simulationMotionModelOptions[0],
    startCoordinate: { lng: '', lat: '', alt: '' },
    endCoordinate: { lng: '', lat: '', alt: '' },
    startVelocity: { lng: '', lat: '', alt: '' },
    startAttitude: { roll: '', heading: '', pitch: '' },
    tabs
  }
}

function createFallbackExperimentOptions(projects = fallbackProjectOptions) {
  return projects.flatMap((project, index) => ([
    {
      experimentId: `E${index + 1}01`,
      experimentName: `${project.projectName}-一次试验`,
      projectId: project.projectId,
      projectName: project.projectName
    },
    {
      experimentId: `E${index + 1}02`,
      experimentName: `${project.projectName}-二次试验`,
      projectId: project.projectId,
      projectName: project.projectName
    }
  ]))
}

function pickRandom(list) {
  if (!list.length) return null
  return list[Math.floor(Math.random() * list.length)]
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

function normalizeProjectOptions(projects) {
  return projects && projects.length ? projects : fallbackProjectOptions
}

function normalizeTargetTypeOptions(targetTypes) {
  return targetTypes && targetTypes.length ? targetTypes : fallbackTargetTypeOptions
}

function normalizeExperimentOptions(experiments, projects) {
  return experiments && experiments.length ? experiments : createFallbackExperimentOptions(projects)
}

function createMockTaskRows(rowCount = 36) {
  const projects = normalizeProjectOptions(projectOptions.value)
  const experiments = normalizeExperimentOptions(experimentOptions.value, projects)
  const targetTypes = normalizeTargetTypeOptions(targetTypeOptions.value)

  return Array.from({ length: rowCount }, (_, index) => {
    const experiment = pickRandom(experiments)
    const project = projects.find(item => String(item.projectId) === String(experiment?.projectId)) || pickRandom(projects)
    const target = pickRandom(targetTypes)
    const taskPrefix = pickRandom(mockTaskPrefixes)
    const dataType = pickRandom(mockDataTypes)
    const createBy = pickRandom(mockCreators)
    const location = pickRandom(mockLocations)
    const status = pickRandom(mockStatusOptions)
    const createTime = Date.now() - randomInt(0, 20) * 24 * 60 * 60 * 1000 - randomInt(0, 18) * 60 * 60 * 1000
    const taskName = `${taskPrefix}${String(index + 1).padStart(2, '0')}`
    const safeProjectName = String(project?.projectName || '模拟项目').replace(/\s+/g, '')
    const safeExperimentName = String(experiment?.experimentName || '模拟试验').replace(/\s+/g, '')
    const safeDataType = String(dataType).replace(/\s+/g, '')

    return {
      id: `TASK-${String(index + 1).padStart(4, '0')}`,
      taskName,
      projectId: project?.projectId || null,
      projectName: project?.projectName || '模拟项目A',
      experimentId: experiment?.experimentId || null,
      experimentName: experiment?.experimentName || '模拟项目A-一次试验',
      dataFilePath: `/mock/${safeProjectName}/${safeExperimentName}/${safeDataType}_${index + 1}.dat`,
      dataType,
      targetType: target?.targetType || '空中目标',
      createBy,
      createTime,
      status,
      isSimulation: false,
      contentDesc: `${taskName} 用于展示当前页面的仿真任务假数据效果`,
      location,
      startTime: createTime
    }
  })
}

function applyMockTaskFilters() {
  const { taskName, projectId, experimentId, createBy, pageNum, pageSize } = queryParams.value

  let rows = [...mockTaskSource.value]
  if (taskName) {
    rows = rows.filter(item => item.taskName.includes(taskName))
  }
  if (projectId) {
    rows = rows.filter(item => String(item.projectId) === String(projectId))
  }
  if (experimentId) {
    rows = rows.filter(item => String(item.experimentId) === String(experimentId))
  }
  if (createBy) {
    rows = rows.filter(item => String(item.createBy).includes(createBy))
  }

  total.value = rows.length

  const currentPage = Number(pageNum) || 1
  const currentSize = Number(pageSize) || 10
  const pageCount = total.value > 0 ? Math.ceil(total.value / currentSize) : 1
  if (currentPage > pageCount) {
    queryParams.value.pageNum = 1
  }

  const start = ((Number(queryParams.value.pageNum) || 1) - 1) * currentSize
  taskList.value = rows.slice(start, start + currentSize)
}

const simulationForm = reactive(createSimulationForm())
const currentSimulationTabForm = computed(() => simulationForm.tabs[activeSimulationTab.value])
const currentSimulationTab = computed(() => {
  return simulationTabs.find(tab => tab.name === activeSimulationTab.value) || simulationTabs[0]
})
const filteredSimulationExperiments = computed(() => {
  if (!simulationForm.projectId) {
    return experimentOptions.value
  }
  return experimentOptions.value.filter(item => String(item.projectId) === String(simulationForm.projectId))
})

const data = reactive({
  form: {},
  queryParams: {
    id: null,
    taskName: null,
    projectId: null,
    experimentId: null,
    createBy:null,
    pageNum: 1,
    pageSize: 10,
  },
  rules: {
    id: [
      { required: true, message: "编号不能为空", trigger: "blur" }
    ],
    name: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    parentId: [
      { required: true, message: "所属项目不能为空", trigger: "change" }
    ],
    startTime: [
      { required: true, message: "时间不能为空", trigger: "blur" }
    ],
    targetId: [
      { required: true, message: "试验目标不能为空", trigger: "change" }
    ],
    location: [
      { required: true, message: "地点不能为空", trigger: "blur" }
    ],
    contentDesc: [
      { required: true, message: "内容描述不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

async function handleAddSimulationData() {
  await getList()
  handleResetSimulationForm()
  simulationDialogOpen.value = true
}

/** 新增试验按钮操作 */
async function handleAddExperiment() {
  reset()
  await loadInfoOptions()
  form.value.type = 'experiment'
  form.value.parentId = null  
  title.value = "添加试验"
  open.value = true
}

async function handleAdd(row) {
  reset()
  if (row != null && row.id) {
    await loadInfoOptions()
    form.value.parentId = row.id
    form.value.type = 'experiment'
    title.value = "添加试验"
  } else {
    form.value.parentId = 0
    form.value.type = 'project'
    title.value = "添加项目"
  }
  open.value = true
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    name: null,
    startTime: null,
    contentDesc: null,
    createTime: null,
    location: null,
    targetType: null,
    targetId: null,
    type: 'project',
    parentId: 0,
    path: null,
    createBy: null
  }
  proxy.resetForm("infoRef")
}

function submitForm() {
  if (submitLoading.value) return
  proxy.$refs["infoRef"].validate(async valid => {
    if (!valid) return
    submitLoading.value = true
    try {
      const submitData = JSON.parse(JSON.stringify(form.value))
      if (submitData.startTime) {
        submitData.startTime = formatDateForSubmit(submitData.startTime)
      }

      if (submitData.id != null && title.value.startsWith("修改")) {
        await updateInfo(submitData.id, submitData.type, submitData)
        proxy.$modal.msgSuccess("修改成功")
      } else {
        await addInfo(submitData)
        proxy.$modal.msgSuccess("新增成功")
      }
      open.value = false
      reset()
      await getList()
    } catch (error) {
      await refreshListAfterAjaxError(error)
    } finally {
      submitLoading.value = false
    }
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

async function loadInfoOptions() {
  try {
    const response = await getInfo(null, 'experiment')
    projectOptions.value = normalizeProjectOptions(response.projects || [])
    targetTypeOptions.value = normalizeTargetTypeOptions(response.targetTypes || [])
  } catch (error) {
    projectOptions.value = [...fallbackProjectOptions]
    targetTypeOptions.value = [...fallbackTargetTypeOptions]
  }
}

async function getList() {
  loading.value = true
  try {
    try {
      const [infoResponse, experimentResponse] = await Promise.all([
        getInfo(null, 'experiment'),
        getExperimentInfos({ pageNum: 1, pageSize: 1000 })
      ])
      projectOptions.value = normalizeProjectOptions(infoResponse.projects || [])
      targetTypeOptions.value = normalizeTargetTypeOptions(infoResponse.targetTypes || [])
      experimentOptions.value = normalizeExperimentOptions(
        experimentResponse.rows || (experimentResponse.data && experimentResponse.data.rows) || [],
        projectOptions.value
      )
    } catch (error) {
      projectOptions.value = [...fallbackProjectOptions]
      targetTypeOptions.value = [...fallbackTargetTypeOptions]
      experimentOptions.value = createFallbackExperimentOptions(projectOptions.value)
    }

    if (!mockTaskSource.value.length) {
      mockTaskSource.value = createMockTaskRows()
    }
    applyMockTaskFilters()
  } finally {
    loading.value = false
  }
}

async function refreshListAfterAjaxError(error) {
  if (isActionCancelled(error)) {
    return
  }
  await getList()
}

function isActionCancelled(error) {
  return error === 'cancel' || error === 'close'
}

function formatDateForSubmit(date) {
  if (!date) return null

  if (typeof date === 'string') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return date
    }
    if (date.includes('CST')) {
      const parts = date.split(/\s+/).filter(item => item)
      if (parts.length >= 6) {
        const monthMap = {
          Jan: '01', Feb: '02', Mar: '03', Apr: '04', May: '05', Jun: '06',
          Jul: '07', Aug: '08', Sep: '09', Oct: '10', Nov: '11', Dec: '12'
        }
        const month = monthMap[parts[1]]
        const day = parts[2]
        const year = parts[5]
        if (month && day && year) {
          return `${year}-${month}-${day.padStart(2, '0')}`
        }
      }
    }
  }

  if (date instanceof Date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  return date
}

function handleQuery() {
  queryParams.value.pageNum = 1
  applyMockTaskFilters()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  queryParams.value = {
    ...queryParams.value,
    id: null,
    taskName: null,
    projectId: null,
    experimentId: null,
    createBy: null,
    pageNum: 1,
    pageSize: 10
  }
  applyMockTaskFilters()
}

function handleSelectionChange(selection) {
  selectedTaskRows.value = selection
  multiple.value = !selection.length
}

function handleDelete(row) {
  const ids = row ? [row.id] : selectedTaskRows.value.map(item => item.id)
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的任务')
    return
  }
  mockTaskSource.value = mockTaskSource.value.filter(item => !ids.includes(item.id))
  selectedTaskRows.value = []
  multiple.value = true
  applyMockTaskFilters()
  ElMessage.success('已删除所选假数据')
}

function handleView(row) {
  form.value = {
    ...form.value,
    ...row
  }
  openView.value = true
}

function handleSimulationProjectChange() {
  const exists = filteredSimulationExperiments.value.some(
    item => String(item.experimentId) === String(simulationForm.experimentId)
  )
  if (!exists) {
    simulationForm.experimentId = null
  }
}

function reindexSimulationDataItems(items) {
  items.forEach((item, index) => {
    item.name = `数据${index + 1}`
  })
}

function addSimulationDataItem() {
  const items = currentSimulationTabForm.value.items
  items.push(createSimulationDataItem(items.length + 1))
}

function removeSimulationDataItem(index) {
  const items = currentSimulationTabForm.value.items
  if (items.length === 1) {
    ElMessage.warning('至少保留一条数据项')
    return
  }
  items.splice(index, 1)
  reindexSimulationDataItems(items)
}

function handleResetSimulationForm() {
  Object.assign(simulationForm, createSimulationForm())
  activeSimulationTab.value = simulationTabs[0].name
}

function handleSaveSimulationPage() {
  ElMessage.success(`已暂存“${currentSimulationTab.value.label}”页签配置`)
}

function handleGenerateSimulation() {
  if (!simulationForm.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (!simulationForm.projectId || !simulationForm.experimentId) {
    ElMessage.warning('请选择所属项目和所属试验')
    return
  }

  const project = projectOptions.value.find(item => String(item.projectId) === String(simulationForm.projectId))
  const experiment = experimentOptions.value.find(item => String(item.experimentId) === String(simulationForm.experimentId))
  const targetType = experiment?.targetType
    || pickRandom(normalizeTargetTypeOptions(targetTypeOptions.value))?.targetType
    || '空中目标'

  mockTaskSource.value.unshift({
    id: `TASK-${Date.now()}`,
    taskName: simulationForm.taskName,
    projectId: simulationForm.projectId,
    projectName: project?.projectName || '模拟项目',
    experimentId: simulationForm.experimentId,
    experimentName: experiment?.experimentName || '模拟试验',
    dataFilePath: `/mock/${project?.projectName || '模拟项目'}/${experiment?.experimentName || '模拟试验'}/${currentSimulationTab.value.label}.dat`,
    dataType: currentSimulationTab.value.label,
    targetType,
    createBy: 'mock-system',
    createTime: Date.now(),
    status: 2,
    isSimulation: false,
    contentDesc: `由“${simulationForm.taskName}”前端仿真配置生成的示例任务`,
    location: pickRandom(mockLocations),
    startTime: Date.now()
  })

  queryParams.value.pageNum = 1
  applyMockTaskFilters()
  simulationDialogOpen.value = false
  ElMessage.success('已生成一条前端假数据任务')
}

onMounted(()=>{
    getList()
})

</script>
<style scoped>
.simulation-panel {
  padding: 4px 6px 12px;
}

.simulation-block {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 28px;
}

.simulation-block__title {
  width: 120px;
  flex-shrink: 0;
  font-size: 18px;
  font-weight: 700;
  color: #20262e;
  line-height: 40px;
}

.simulation-block__body {
  flex: 1;
  min-width: 0;
}

.simulation-grid {
  display: grid;
  gap: 18px 24px;
}

.simulation-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.simulation-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.simulation-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.simulation-field--wide {
  max-width: 760px;
}

.simulation-field__label {
  width: 88px;
  flex-shrink: 0;
  color: #303133;
  font-size: 15px;
}

.triple-inputs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.frame-rate-field {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.frame-rate-field :deep(.el-select) {
  flex: 1;
}

.frame-rate-unit {
  color: #909399;
}

.simulation-block--data {
  align-items: stretch;
}

.simulation-tabs {
  border-top: 1px solid #dcdfe6;
}

.simulation-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.simulation-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 700;
  padding: 0 18px;
}

.simulation-data-panel {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 20px 24px 24px;
  min-height: 520px;
}

.simulation-item-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 28px;
}

.simulation-item-row {
  display: grid;
  grid-template-columns: 110px minmax(0, 320px) minmax(0, 320px) 44px;
  gap: 18px;
  align-items: center;
}

.simulation-item-name {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f7f8fa;
  color: #303133;
}

.simulation-item-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.simulation-item-field__label {
  width: 60px;
  color: #303133;
  flex-shrink: 0;
}

.simulation-item-delete {
  justify-self: center;
  font-size: 18px;
}

.simulation-data-actions {
  margin-top: 14px;
}

.simulation-add-item-btn {
  min-width: 120px;
  background: #353840;
  border-color: #353840;
  color: #fff;
}

.simulation-add-item-btn:hover,
.simulation-add-item-btn:focus {
  background: #4d525c;
  border-color: #4d525c;
  color: #fff;
}

.simulation-subpage-save {
  display: flex;
  justify-content: center;
  margin-top: 170px;
}

.simulation-save-btn {
  min-width: 360px;
  height: 40px;
  border: none;
  background: linear-gradient(90deg, #1328ff 0%, #243bff 100%);
  box-shadow: 0 8px 18px rgba(36, 59, 255, 0.2);
}

.simulation-footer {
  display: flex;
  justify-content: center;
  gap: 36px;
  margin-top: 8px;
}

.simulation-footer__reset,
.simulation-footer__confirm {
  min-width: 180px;
  height: 48px;
  font-size: 16px;
  border: none;
}

.simulation-footer__reset {
  background: #b5b7bd;
  color: #fff;
}

.simulation-footer__confirm {
  background: linear-gradient(90deg, #6b6df7 0%, #7c82ff 100%);
}

@media (max-width: 1440px) {
  .simulation-block {
    flex-direction: column;
    gap: 10px;
  }

  .simulation-block__title {
    width: auto;
    line-height: 1.2;
  }

  .simulation-grid--three {
    grid-template-columns: 1fr;
  }

  .simulation-item-row {
    grid-template-columns: 1fr;
  }
}
</style>
