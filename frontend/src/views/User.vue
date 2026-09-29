<template>
  <div class="user-page">
    <div class="user-grid">
      <!-- 左:资料卡 -->
      <aside class="me-card">
        <div class="me-head">
          <el-avatar :size="72" class="avatar">{{ meChar }}</el-avatar>
          <h2>{{ me?.nickName || '同学' }}</h2>
          <p>{{ info?.introduce || '这个人很低调,什么也没写…' }}</p>
        </div>

        <div class="me-stats">
          <div class="st" @click="$router.push('/follow/' + me.id)">
            <b>{{ info?.followee ?? 0 }}</b><span>关注</span>
          </div>
          <div class="st">
            <b>{{ info?.fans ?? 0 }}</b><span>粉丝</span>
          </div>
          <div class="st">
            <b>{{ info?.credits ?? 0 }}</b><span>积分</span>
          </div>
        </div>

        <el-divider />

        <div class="sign-card">
          <div class="sign-head">
            <div>
              <h4>📅 每日签到</h4>
              <p class="sign-desc">连续签到 {{ signCount }} 天</p>
            </div>
            <el-button type="primary" round :loading="signing" @click="doSign">签到</el-button>
          </div>
          <el-progress
            :percentage="Math.min(100, signCount * 8)"
            :stroke-width="8"
            striped
            :show-text="false"
            style="margin-top: 12px"
          />
          <p class="sign-tip">连续签到可提升专属 Token 折扣等级 👑</p>
        </div>

        <el-divider />

        <div class="me-menu">
          <el-button text :icon="Monitor" @click="$router.push('/devices')">我的在线设备</el-button>
          <el-button text :icon="EditPen" @click="$router.push('/publish')">发布灵感</el-button>
          <el-button text :icon="SwitchButton" type="danger" @click="logout">退出登录</el-button>
        </div>
      </aside>

      <!-- 右:我的发布 -->
      <section class="my-blog">
        <div class="mb-head">
          <h3>📝 我的灵感</h3>
          <span class="mb-sub">共 {{ myBlogs.length }} 条</span>
        </div>
        <div v-loading="loading" class="mb-list">
          <BlogCard v-for="b in myBlogs" :key="b.id" :blog="b" style="margin-bottom: 14px" />
          <el-empty v-if="!myBlogs.length && !loading" description="还没有发布过灵感,去分享第一条吧" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, EditPen, SwitchButton } from '@element-plus/icons-vue'
import { apiMe, apiUserInfo, apiSign, apiSignCount, apiMyBlog, apiLogout } from '../api'
import { useUserStore } from '../stores/user'
import BlogCard from '../components/BlogCard.vue'

const router = useRouter()
const userStore = useUserStore()
const me = ref(null)
const info = ref(null)
const signCount = ref(0)
const signing = ref(false)
const myBlogs = ref([])
const loading = ref(false)

const meChar = computed(() => (me.value?.nickName || '我').slice(0, 1))

async function load() {
  me.value = await apiMe()
  userStore.me = me.value
  try {
    info.value = await apiUserInfo(me.value.id)
  } catch (e) {}
  try {
    signCount.value = (await apiSignCount()) || 0
  } catch (e) {}
  loading.value = true
  try {
    const list = (await apiMyBlog(1)) || []
    // 后端 of/me 不填充作者信息,前端口径补齐昵称便于卡片展示
    myBlogs.value = list.map((b) => ({ ...b, name: b.name || me.value?.nickName || '我' }))
  } finally {
    loading.value = false
  }
}

async function doSign() {
  signing.value = true
  try {
    await apiSign()
    signCount.value = (await apiSignCount()) || 0
    ElMessage.success('签到成功,算力折扣 +1 🎉')
  } catch (e) {
    /* 拦截器提示 */
  } finally {
    signing.value = false
  }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗?', '退出登录', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await apiLogout()
  } catch (e) {}
  userStore.clear()
  router.push('/login')
}

onMounted(load)
</script>

<style scoped>
.user-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
  align-items: start;
}
.me-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 24px 24px 12px;
  position: sticky;
  top: 84px;
}
.me-head {
  text-align: center;
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-size: 28px;
}
.me-head h2 {
  margin: 12px 0 4px;
  font-size: 18px;
}
.me-head p {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
}
.me-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
}
.st {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  transition: color 0.2s;
}
.st:hover {
  color: var(--brand);
}
.st b {
  font-size: 17px;
}
.st span {
  font-size: 11px;
  color: var(--text-sub);
}
.sign-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sign-head h4 {
  margin: 0;
  font-size: 15px;
}
.sign-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--brand);
}
.sign-tip {
  margin: 8px 0 0;
  font-size: 11px;
  color: #aab;
  text-align: center;
}
.me-menu {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  padding-bottom: 12px;
}
.me-menu .el-button {
  justify-content: flex-start;
  text-align: left;
}
.my-blog {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 20px;
}
.mb-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.mb-head h3 {
  margin: 0;
  font-size: 17px;
}
.mb-sub {
  font-size: 12px;
  color: var(--text-sub);
}
</style>