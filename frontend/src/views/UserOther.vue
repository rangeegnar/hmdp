<template>
  <div class="user-other">
    <el-page-header @back="$router.back()" content="个人主页" style="margin-bottom: 16px" />

    <div class="other-grid">
      <!-- 左:用户卡 -->
      <aside v-if="user" class="u-card">
        <div class="u-head">
          <el-avatar :size="76" class="avatar">{{ uChar }}</el-avatar>
          <h2>{{ user.nickName || '同学' }}</h2>
          <p class="u-city">
            <el-icon><Location /></el-icon>{{ info?.city || '未知城市' }}
            <span v-if="info?.level" class="lv">👑 {{ info.gender === 1 ? '女神' : '大神' }} Lv.{{ info.level }}</span>
          </p>
          <p class="u-intro">{{ info?.introduce || '这个人很低调…' }}</p>
        </div>

        <div class="u-stats">
          <div class="st"><b>{{ info?.followee ?? 0 }}</b><span>关注</span></div>
          <div class="st"><b>{{ info?.fans ?? 0 }}</b><span>粉丝</span></div>
          <div class="st"><b>{{ info?.credits ?? 0 }}</b><span>积分</span></div>
        </div>

        <div class="u-actions">
          <el-button
            :type="isFollowed ? 'default' : 'primary'"
            round
            :loading="followLoading"
            @click="toggleFollow"
          >
            {{ isFollowed ? '已关注 ✓' : '+ 关注 TA' }}
          </el-button>
          <el-button round :disabled="commonCount <= 0" @click="$router.push(`/follow/${user.id}`)">
            共同关注 {{ commonCount > 0 ? `(${commonCount})` : '' }}
          </el-button>
        </div>

        <div class="u-meta">
          <p><el-icon><Calendar /></el-icon>生日:{{ info?.birthday || '未填写' }}</p>
          <p><el-icon><Clock /></el-icon>加入:{{ formatTime(user.createTime) }}</p>
        </div>
      </aside>
      <el-skeleton v-else :rows="8" animated style="background: #fff; border-radius: 12px; padding: 24px; grid-column: 1 / -1" />

      <!-- 右:TA 的灵感 -->
      <section v-if="user" class="u-blog">
        <div class="ub-head">
          <h3>📝 {{ user.nickName }} 的灵感</h3>
        </div>
        <div v-loading="loading" class="ub-list">
          <BlogCard v-for="b in blogs" :key="b.id" :blog="b" style="margin-bottom: 14px" />
          <el-empty v-if="!blogs.length && !loading" description="TA 还没有发布过灵感" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiUser, apiUserInfo, apiIsFollow, apiFollow, apiFollowCommon, apiUserBlog } from '../api'
import { formatTime } from '../utils/format'
import { useUserStore } from '../stores/user'
import BlogCard from '../components/BlogCard.vue'

const route = useRoute()
const userStore = useUserStore()
const user = ref(null)
const info = ref(null)
const isFollowed = ref(false)
const commonCount = ref(0)
const followLoading = ref(false)
const blogs = ref([])
const loading = ref(false)

const uChar = computed(() => (user.value?.nickName || '·').slice(0, 1))
const isMe = computed(() => !!userStore.userId && user.value?.id === userStore.userId)

async function load() {
  // 兜底:刷新页面后 userStore.me 可能为空,先拉一次用于"是否本人"判断
  if (userStore.isLogin && !userStore.me) userStore.fetchMe()
  const id = route.params.id
  try {
    user.value = await apiUser(id)
  } catch (e) {
    ElMessage.error('用户不存在')
    return
  }
  try {
    info.value = await apiUserInfo(id)
  } catch (e) {}
  if (!isMe.value) {
    try {
      isFollowed.value = !!(await apiIsFollow(id))
      const list = (await apiFollowCommon(id)) || []
      commonCount.value = list.length
    } catch (e) {}
  }
  loading.value = true
  try {
    blogs.value = (await apiUserBlog(id, 1)) || []
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  followLoading.value = true
  try {
    await apiFollow(user.value.id, !isFollowed.value)
    isFollowed.value = !isFollowed.value
    info.value.fans = (info.value.fans || 0) + (isFollowed.value ? 1 : -1)
    ElMessage.success(isFollowed.value ? '关注成功' : '已取消关注')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    followLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.other-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
  align-items: start;
}
.u-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 24px;
  position: sticky;
  top: 84px;
}
.u-head {
  text-align: center;
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-size: 30px;
}
.u-head h2 {
  margin: 12px 0 4px;
  font-size: 18px;
}
.u-city {
  margin: 0;
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.lv {
  margin-left: 6px;
  background: var(--brand-bg);
  color: var(--brand);
  border-radius: 10px;
  padding: 1px 8px;
}
.u-intro {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}
.u-stats {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  padding-bottom: 14px;
  border-bottom: 1px dashed #eef0ff;
}
.st {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.st b {
  font-size: 17px;
}
.st span {
  font-size: 11px;
  color: var(--text-sub);
}
.u-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.u-actions .el-button {
  flex: 1;
}
.u-meta {
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-sub);
}
.u-meta p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
}
.u-blog {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 20px;
}
.ub-head {
  margin-bottom: 14px;
}
.ub-head h3 {
  margin: 0;
  font-size: 17px;
}
</style>