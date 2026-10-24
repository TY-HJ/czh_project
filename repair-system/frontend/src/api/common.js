import request from '../utils/request'

// 消息
export const getMessages = params => request.get('/messages', { params })
export const getUnreadCount = () => request.get('/messages/unread-count')
export const readAllMessages = () => request.put('/messages/read-all')
export const readMessage = id => request.put(`/messages/${id}/read`)

// 公告
export const getNoticePage = params => request.get('/notices/page', { params })
export const addNotice = data => request.post('/notices', data)
export const updateNotice = data => request.put('/notices', data)
export const deleteNotice = id => request.delete(`/notices/${id}`)

// 统计
export const getOverview = () => request.get('/stats/overview')
export const getTypeDistribution = () => request.get('/stats/type-distribution')
export const getMonthlyTrend = () => request.get('/stats/monthly-trend')
export const getWorkerRanking = () => request.get('/stats/worker-ranking')
