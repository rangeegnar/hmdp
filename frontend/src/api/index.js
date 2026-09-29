import request from './request'

/**
 * 全部后端接口定义。后端统一返回 Result{success,errorMsg,data},
 * 封装层已自动解包,此处拿到的即 data。
 */

// ---------------- 用户 /user ----------------
export const apiSendCode = (phone) => request.post('/user/code', null, { params: { phone } })
export const apiLoginByCode = (data) => request.post('/user/login', data) // {phone, code} -> token
export const apiLoginByPassword = (data) => request.post('/user/loginWithPassword', data) // {phone, password}
export const apiLogout = () => request.post('/user/logout')
export const apiMe = () => request.get('/user/me') // UserDTO{id,nickName,icon}
export const apiDevices = () => request.get('/user/devices') // [{token,loginTime}]
export const apiKickDevice = (token) => request.post('/user/devices/kick', null, { params: { token } })
export const apiKickAll = () => request.post('/user/kickAll')
export const apiUserInfo = (id) => request.get(`/user/info/${id}`) // UserInfo(个人资料扩展)
export const apiUser = (id) => request.get(`/user/${id}`) // UserDTO
export const apiSign = () => request.post('/user/sign')
export const apiSignCount = () => request.get('/user/sign/count') // 连续签到天数

// ---------------- 商铺(高校) /shop ----------------
export const apiShop = (id) => request.get(`/shop/${id}`) // 高校详情
export const apiShopByType = (params) => request.get('/shop/of/type', { params }) // {typeId,current,x?,y?}
export const apiShopByName = (params) => request.get('/shop/of/name', { params }) // {name,current}

// ---------------- 商铺类型 /shop-type ----------------
export const apiShopTypes = () => request.get('/shop-type/list')

// ---------------- 笔记(灵感) /blog ----------------
export const apiHotBlog = (current) => request.get('/blog/hot', { params: { current } })
export const apiBlog = (id) => request.get(`/blog/${id}`)
export const apiMyBlog = (current) => request.get('/blog/of/me', { params: { current } })
export const apiUserBlog = (id, current) => request.get('/blog/of/user', { params: { id, current } })
export const apiBlogLikes = (id) => request.get(`/blog/likes/${id}`) // UserDTO[]
export const apiLikeBlog = (id) => request.put(`/blog/like/${id}`)
export const apiFollowFeed = (params) => request.get('/blog/of/follow', { params }) // {lastId,offset} -> ScrollResult
export const apiSaveBlog = (data) => request.post('/blog', data)

// ---------------- 关注 /follow ----------------
export const apiFollow = (id, isFollow) => request.put(`/follow/${id}/${isFollow}`)
export const apiIsFollow = (id) => request.get(`/follow/or/not/${id}`)
export const apiFollowCommon = (id) => request.get(`/follow/common/${id}`)

// ---------------- 优惠券(Token 套餐) /voucher ----------------
export const apiVoucherList = (shopId) => request.get(`/voucher/list/${shopId}`)

// ---------------- 秒杀下单 /voucher-order ----------------
export const apiSeckillVoucher = (voucherId) => request.post(`/voucher-order/seckill/${voucherId}`)

// ---------------- 上传 /upload ----------------
export const apiUploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/upload/blog', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const apiDeleteImage = (name) => request.delete('/upload/blog/delete', { params: { name } })