<template>
  <header class="top-nav">
    <div class="nav-inner">
      <router-link to="/" class="logo">
        <span class="logo-badge">🎓</span>
        <span class="logo-text">TokenMall</span>
        <span class="logo-sub">校园 AI 算力商城</span>
      </router-link>

      <nav class="nav-menu">
        <router-link to="/" active-class="active" exact-active-class="active">首页</router-link>
        <router-link to="/seckill">限时秒杀</router-link>
        <router-link to="/blog">灵感广场</router-link>
        <router-link to="/publish">发布灵感</router-link>
        <router-link to="/about">关于</router-link>
      </nav>

      <div class="nav-search">
        <el-input
          v-model="kw"
          placeholder="搜索高校 / AI 算力"
          clearable
          @keyup.enter="doSearch"
          @clear="clearSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <div class="nav-user">
        <template v-if="userStore.isLogin">
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-avatar :size="32" class="avatar">{{ avatarText }}</el-avatar>
              <span class="nick">{{ userStore.me?.nickName || '同学' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="user"><el-icon><User /></el-icon>我的主页</el-dropdown-item>
                <el-dropdown-item command="devices"><el-icon><Monitor /></el-icon>设备管理</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button v-else type="primary" round @click="$router.push('/login')">登录 / 注册</el-button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { apiLogout } from '../api'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const kw = ref('')

const avatarText = computed(() => (userStore.me?.nickName || '同').slice(0, 1))

function doSearch() {
  if (!kw.value.trim()) return
  router.push({ path: '/', query: { name: kw.value.trim() } })
}
function clearSearch() {
  if (route.query.name) router.push({ path: '/' })
}
function onCommand(cmd) {
  if (cmd === 'user') router.push('/user')
  else if (cmd === 'devices') router.push('/devices')
  else if (cmd === 'logout') confirmLogout()
}
async function confirmLogout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗?', '退出登录', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await apiLogout()
  } catch (e) {
    /* 忽略后端错误,本地也强制清理 */
  }
  userStore.clear()
  ElMessage.success && null
  router.push('/login')
}

onMounted(() => {
  if (userStore.isLogin && !userStore.me) userStore.fetchMe()
})
</script>

<style scoped>
.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e5e7ff;
}
.nav-inner {
  max-width: 1280px;
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 20px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
}
.logo-badge {
  font-size: 26px;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.logo-sub {
  font-size: 12px;
  color: var(--text-sub);
  border-left: 1px solid #d5d8ff;
  padding-left: 10px;
}
.nav-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}
.nav-menu a {
  padding: 8px 14px;
  border-radius: 8px;
  color: var(--text-sub);
  font-size: 14px;
  transition: all 0.2s;
}
.nav-menu a:hover {
  color: var(--brand);
  background: var(--brand-bg);
}
.nav-menu a.active {
  color: var(--brand);
  background: var(--brand-bg);
  font-weight: 600;
}
.nav-search {
  flex: 1;
  max-width: 320px;
  margin-left: auto;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-chip:hover {
  background: var(--brand-bg);
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-weight: 600;
}
.nick {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
</style>