import request from '@/utils/request'

export function listSysLogs() {
  return request({
    url: '/monitor/system/log/list',
    method: 'get',
  })
}

export function previewLog(logName) {
  return request({
    url: '/monitor/system/log/preview/' + logName,
    method: 'post',
  })
}

export function downloadLog(fileName) {
  return request({
    url: '/monitor/system/log/download/' + fileName,
    method: 'post',
    responseType: 'blob'
  })
}