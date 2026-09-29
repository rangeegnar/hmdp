<template>
  <div class="blog-page">
    <div class="page-head">
      <h2>💡 灵感广场</h2>
      <p>大学生 AI 使用心得 / 提示词技巧 / 开源分享 · 热门按点赞排序</p>
    </div>

    <el-tabs v-model="tab" class="blog-tabs" @tab-change="onTabChange">
      <el-tab-pane label="🔥 热门" name="hot" />
      <el-tab-pane label="👀 关注动态" name="follow" />
    </el-tabs>

    <div v-loading="loading" class="blog-list">
      <BlogCard v-for="b in list" :key="b.id" :blog="b" style="margin-bottom: 16px" />
    </div>

    <!-- 滚动加载:关注流滚动分页;热门流页码分页 -->
    <div ref="sentinel" class="sentinel">
      <span v-if="loading">加载中…</span>
      <el-button v-else-if="!loading && noMore" text disabled>— 已经到底啦 —</el-button>
      <span v-else-if="!loading && !error"> </span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { apiHotBlog, apiFollowFeed } from '../api'
import BlogCard from '../components/BlogCard.vue'

const tab = ref('hot')
const list = ref([])
const loading = ref(false)
const noMore = ref(false)
const error = ref(false)

// 热门流分页
const hotCurrent = ref(0)
// 关注流滚动
const lastId = ref(0)
const offset = ref(0)

const sentinel = ref(null)
let observer = null

async function loadHot(reset = false) {
  if (reset) {
    list.value = []
    hotCurrent.value = 0
    noMore.value = false
  }
  loading.value = true
  error.value = false
  try {
    const r = await apiHotBlog(hotCurrent.value + 1)
    const arr = r || []
    list.value = reset ? arr : [...list.value, ...arr]
    hotCurrent.value += 1
    noMore.value = arr.length < 10
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function loadFollow(reset = false) {
  if (reset) {
    list.value = []
    lastId.value = 0
    offset.value = 0
    noMore.value = false
  }
  loading.value = true
  error.value = false
  try {
    const r = await apiFollowFeed({ lastId: lastId.value, offset: offset.value })
    const arr = r?.list || []
    list.value = reset ? arr : [...list.value, ...arr]
    lastId.value = Number(r?.minTime || 0)
    offset.value = Number(r?.offset || 0)
    noMore.value = arr.length === 0
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

function onTabChange(name) {
  list.value = []
  noMore.value = false
  if (name === 'hot') loadHot(true)
  else loadFollow(true)
}

// 滚动触底加载(IntersectionObserver)
function setupObserver() {
  observer = new IntersectionObserver((entries) => {
    if (!entries[0].isIntersecting) return
    if (loading.value || noMore.value) return
    if (tab.value === 'hot') loadHot()
    else loadFollow()
  }, { rootMargin: '120px' })
  if (sentinel.value) observer.observe(sentinel.value)
}

onMounted(async () => {
  await loadHot(true)
  setupObserver()
})
onBeforeUnmount(() => observer && observer.disconnect())
</script>

<style scoped>
.page-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.page-head p {
  margin: 0 0 16px;
  color: var(--text-sub);
  font-size: 13px;
}
.blog-tabs :deep(.el-tabs__item) {
  font-size: 15px;
}
.blog-list {
  min-height: 200px;
  max-width: 860px;
  margin: 0 auto;
  padding-top: 6px;
}
.sentinel {
  text-align: center;
  color: #aab;
  font-size: 13px;
  padding: 12px 0 24px;
  min-height: 40px;
}
</style>