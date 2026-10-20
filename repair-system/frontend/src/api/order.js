import request from '../utils/request'

// 住户
export const createOrder = data => request.post('/orders', data)
export const getMyOrders = params => request.get('/orders/my', { params })
export const acceptOrder = (id, data) => request.put(`/orders/${id}/acceptance`, data)
export const cancelOrder = id => request.put(`/orders/${id}/cancel`)

// 维修工
export const getWorkerOrders = params => request.get('/orders/worker', { params })
export const finishOrder = (id, data) => request.put(`/orders/${id}/finish`, data)

// 管理员
export const getAdminOrders = params => request.get('/orders/page', { params })
export const assignOrder = (id, data) => request.put(`/orders/${id}/assign`, data)
export const closeOrder = id => request.put(`/orders/${id}/close`)

// 公共
export const urgeOrder = id => request.put(`/orders/${id}/urge`)
export const getOrderDetail = id => request.get(`/orders/${id}`)
export const getOrderLogs = id => request.get(`/orders/${id}/logs`)

// 图片上传
export const uploadUrl = '/api/files/upload'
