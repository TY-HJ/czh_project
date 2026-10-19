import request from '../utils/request'

// 用户管理(管理员)
export const getUserPage = params => request.get('/users/page', { params })
export const getUsersByRole = role => request.get(`/users/role/${role}`)
export const addUser = data => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const toggleUserStatus = id => request.put(`/users/${id}/toggle-status`)
export const deleteUser = id => request.delete(`/users/${id}`)

// 字典
export const getTypes = () => request.get('/dict/types')
export const addType = data => request.post('/dict/types', data)
export const updateType = data => request.put('/dict/types', data)
export const deleteType = id => request.delete(`/dict/types/${id}`)
export const getLocations = () => request.get('/dict/locations')
export const addLocation = data => request.post('/dict/locations', data)
export const updateLocation = data => request.put('/dict/locations', data)
export const deleteLocation = id => request.delete(`/dict/locations/${id}`)
