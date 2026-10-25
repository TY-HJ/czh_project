import request from '../utils/request'

// 智能派单规则(管理员)
export const getDispatchRule = () => request.get('/dispatch/rule')
export const saveDispatchRule = data => request.put('/dispatch/rule', data)

// 候选人得分明细(手动派单弹窗数据源)
export const getCandidates = id => request.get(`/dispatch/candidates/${id}`)
