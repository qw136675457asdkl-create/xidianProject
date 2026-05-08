import request from '@/utils/request'

export function listInfo(query) {
  return request({
    url: '/data/info/list',
    method: 'get',
    params: query
  })
}
export function getExperimentTree(query) {
  return request({
    url: '/data/info/tree',
    method: 'get',
    params: query
})   
}

export function getInfo(id, type, config = {}) {
  return request({
    url: id ? '/data/info/' + id : '/data/info/',
    method: 'get',
    params: { type },
    ...config
  })
}

export function addProjectInfo(data, config = {}) {
  return request({
    url: '/data/info/project',
    method: 'post',
    data,
    ...config
  })
}

export function addExperimentInfo(data, config = {}) {
  const requestConfig = {
    url: '/data/info/experiment',
    method: 'post',
    data,
    timeout: config.timeout ?? 6 * 60 * 60 * 1000,
    ...config
  }

  if (data instanceof FormData) {
    requestConfig.headers = {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false,
      ...(config.headers || {})
    }
  }

  return request(requestConfig)
}

export function completeExperimentFolderUpload(data, config = {}) {
  return request({
    url: '/data/info/experiment/folder/complete',
    method: 'post',
    data,
    timeout: config.timeout ?? 60 * 60 * 1000,
    ...config
  })
}

export function addInfo(data, config = {}) {
  if (data?.type === 'experiment') {
    return addExperimentInfo(data, config)
  }
  return addProjectInfo(data, config)
}

export function updateInfo(id, type, data, config = {}) {
  return request({
    url: '/data/info/' + id,
    method: 'put',
    params: { type },
    data,
    ...config
  })
}

export function delInfo(id, type, config = {}) {
  return request({
    url: '/data/info/' + id,
    method: 'delete',
    params: { type },
    ...config
  })
}

export function delInfoBatch(experimentIds, projectIds) {
  return request({
    url: '/data/info/' + experimentIds + '/project/' + projectIds,
    method: 'delete'
  })
}

export function getprojectList() {
  return request({
    url: '/data/info/projectList',
    method: 'get'
  })
}

export function getTargetInfos() {
  return request({
    url: '/data/info/targetTypeList',
    method: 'get'
  })
}

export function getExperimentInfos(experimentInfoQuery) {
  return request({
    url: '/data/info/experimentInfos',
    method: 'get',
    params: experimentInfoQuery
  })
}
