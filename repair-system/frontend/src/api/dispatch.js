import request from '../utils/request'

// 智能派单规则(管理员)
export const getDispatchRule = () => request.get('/dispatch/rule')
export const saveDispatchRule = data => request.put('/dispatch/rule', data)

// 派单弹窗候选列表: 在岗师傅 + 技能匹配标记 + 实时得分(缺技能/满负荷的人也在,由管理员判断)
export const getAssignCandidates = id => request.get(`/dispatch/assign-candidates/${id}`)
