<template>
  <div v-if="blog" class="blog-detail">
    <el-page-header @back="$router.back()" :content="blog.title" style="margin-bottom: 16px" />

    <div class="detail-card">
      <!-- 作者行 -->
      <div class="author-row" @click="goUser(blog.userId)">
        <el-avatar :size="44" class="avatar">{{ authorChar }}</el-avatar>
        <div class="a-info">
          <span class="a-name">{{ author?.nickName || blog.name || '同学' }}</span>
          <span class="a-time">{{ formatTime(blog.createTime, true) }}</span>
        </div>
        <span class="a-intro">{{ info?.introduce || '' }}</span>
      </div>

      <h1 class="title">{{ blog.title }}</h1>

      <p class="content">{{ blog.content }}</p>

      <!-- 配图 -->
      <div v-if="images.length" class="gallery">
        <el-image
          v-for="(img, i) in images"
          :key="i"
          :src="resolveImg(img)"
          fit="cover"
          class="gallery-img"
          :preview-src-list="images.map(resolveImg)"
          :initial-index="i"
        />
      </div>

      <!-- 点赞 -->
      <div class="like-bar">
        <el-button
          :type="blog.isLike ? 'danger' : 'default'"
          :plain="!blog.isLike"
          round
          size="large"
          @click="toggleLike"
        >
          <el-icon style="margin-right: 4px"><Star /></el-icon>
          {{ blog.isLike ? '已赞' : '点赞' }} {{ blog.liked ?? 0 }}
        </el-button>

        <!-- 点赞 Top5 -->
        <div v-if="likes.length" class="like-users">
          <el-tooltip v-for="u in likes" :key="u.id" :content="u.nickName" placement="top">
            <el-avatar
              :size="30"
              class="avatar small"
              :style="{ cursor: 'pointer' }"
              @click="goUser(u.id)"
            >{{ (u.nickName || '·').slice(0, 1) }}</el-avatar>
          </el-tooltip>
          <span class="like-tip">共 {{ blog.liked }} 人赞过</span>
        </div>
      </div>
    </div>

    <!-- 评论区:后端暂无接口 -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="评论功能开发中(后端 blog-comments 接口尚未实现),敬请期待"
      class="comment-tip"
    />
  </div>
  <el-skeleton v-else :rows="8" animated style="margin-top: 40px" />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiBlog, apiBlogLikes, apiLikeBlog, apiUser, apiUserInfo } from '../api'
import { formatTime, resolveImg, splitImages } from '../utils/format'

const route = useRoute()
const router = useRouter()

const blog = ref(null)
const likes = ref([])
const author = ref(null)
const info = ref(null)

const authorChar = computed(() => (blog.value?.name || '同').slice(0, 1))
const images = computed(() => splitImages(blog.value?.images))

function goUser(id) {
  router.push(`/user/${id}`)
}

async function loadAuthor() {
  try {
    author.value = await apiUser(blog.value.user_id)
  } catch (e) {}
  try {
    info.value = await apiUserInfo(blog.value.user_id)
  } catch (e) {}
}

async function load() {
  blog.value = await apiBlog(route.params.id)
  loadAuthor()
  loadLikes()
}

async function loadLikes() {
  likes.value = (await apiBlogLikes(route.params.id)) || []
}

async function toggleLike() {
  try {
    await apiLikeBlog(route.params.id)
    blog.value.isLike = !blog.value.isLike
    blog.value.liked = (blog.value.liked ?? 0) + (blog.value.isLike ? 1 : -1)
    if (blog.value.isLike) loadLikes()
    ElMessage.success(blog.value.isLike ? '点赞成功' : '已取消点赞')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.detail-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 28px 32px 24px;
  max-width: 860px;
  margin: 0 auto;
}
.author-row {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-weight: 600;
}
.a-info {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.a-name {
  font-size: 15px;
  font-weight: 600;
}
.a-time {
  font-size: 12px;
  color: #a0a5bb;
}
.a-intro {
  margin-left: auto;
  font-size: 12px;
  color: var(--brand);
  background: var(--brand-bg);
  padding: 3px 10px;
  border-radius: 12px;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.title {
  margin: 20px 0 14px;
  font-size: 22px;
  line-height: 1.5;
}
.content {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.9;
  color: #373a47;
  margin: 0 0 16px;
}
.gallery {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 20px;
}
.gallery-img {
  width: 100%;
  height: 200px;
  border-radius: 10px;
}
.like-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  border-top: 1px dashed #eef0ff;
  padding-top: 18px;
}
.like-users {
  display: flex;
  align-items: center;
  gap: 4px;
}
.avatar.small {
  background: #c7cbff;
  color: #4f46e5;
}
.like-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-sub);
}
.comment-tip {
  max-width: 860px;
  margin: 16px auto 0;
}
</style>