import request from '../utils/request'

// 个人中心(全部角色)
export const getProfile = () => request.get('/profile')
export const updateProfile = data => request.put('/profile', data)
export const changePassword = data => request.put('/profile/password', data)
