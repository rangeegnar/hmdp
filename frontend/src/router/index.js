import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../views/Home.vue'), meta: { title: '首页' } },
      { path: 'shop/:id', name: 'shop-detail', component: () => import('../views/ShopDetail.vue'), meta: { title: '高校详情' } },
      { path: 'seckill', name: 'seckill', component: () => import('../views/Seckill.vue'), meta: { title: '限时秒杀' } },
      { path: 'blog', name: 'blog', component: () => import('../views/Blog.vue'), meta: { title: '灵感广场' } },
      { path: 'blog/:id', name: 'blog-detail', component: () => import('../views/BlogDetail.vue'), meta: { title: '灵感详情' } },
      { path: 'publish', name: 'publish', component: () => import('../views/Publish.vue'), meta: { title: '发布灵感' } },
      { path: 'user', name: 'user', component: () => import('../views/User.vue'), meta: { title: '我的' } },
      { path: 'user/:id', name: 'user-other', component: () => import('../views/UserOther.vue'), meta: { title: '个人主页' } },
      { path: 'devices', name: 'devices', component: () => import('../views/Devices.vue'), meta: { title: '设备管理' } },
      { path: 'follow/:id', name: 'follow-common', component: () => import('../views/FollowCommon.vue'), meta: { title: '共同关注' } },
      { path: 'about', name: 'about', component: () => import('../views/About.vue'), meta: { title: '关于' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 匿名可访问页面(与后端 LoginInterceptor 白名单对齐;下单/点赞等敏感操作仍需登录,401 时拦截器会跳登录)
const PUBLIC_ROUTES = ['login', 'home', 'shop-detail', 'about', 'seckill']

// 登录守卫:除公开页外均需登录
router.beforeEach((to) => {
  const token = localStorage.getItem('hm_token')
  if (!token && !PUBLIC_ROUTES.includes(to.name)) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (token && to.name === 'login') {
    return { path: '/' }
  }
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + 'TokenMall'
  return true
})

export default router