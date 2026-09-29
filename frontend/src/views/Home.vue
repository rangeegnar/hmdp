<template>
  <div class="home">
    <!-- 顶部秒杀横幅 -->
    <div class="hero">
      <div class="hero-text">
        <h2>⚡ 大模型 Token 限时秒杀</h2>
        <p>10 元抢 10 万 Token · 29.9 元抢 50 万 Token · 高校学生专属</p>
      </div>
      <el-button type="danger" size="large" round @click="$router.push('/seckill')">立即抢购 →</el-button>
    </div>

    <!-- 搜索态 -->
    <template v-if="searchMode">
      <div class="section-bar">
        <h3>「{{ route.query.name }}」的搜索结果</h3>
        <el-button text type="primary" @click="exitSearch"><el-icon><Refresh /></el-icon> 返回分类浏览</el-button>
      </div>
      <div class="grid">
        <CampusCard v-for="s in searchList" :key="s.id" :shop="s" :type-name="typeNameOf(s.typeId)" />
      </div>
      <el-empty v-if="!searchList.length" description="没有找到匹配的高校" />
    </template>

    <!-- 分类浏览态 -->
    <template v-else>
      <div class="section-bar">
        <h3>🏫 高校能量站</h3>
        <span class="bar-sub">聚合高校 · 学生专属算力</span>
      </div>
      <el-menu
        :default-active="String(activeType)"
        mode="horizontal"
        class="type-menu"
        @select="onSelectType"
      >
        <el-menu-item index="all">全部</el-menu-item>
        <el-menu-item v-for="t in types" :key="t.id" :index="String(t.id)">{{ t.name }}</el-menu-item>
      </el-menu>

      <div class="grid">
        <CampusCard v-for="s in schoolList" :key="s.id" :shop="s" :type-name="typeNameOf(s.typeId)" />
      </div>

      <el-empty v-if="!schoolList.length && !loadingMore" description="该分类下暂无高校" />

      <div class="load-more">
        <el-button v-if="hasMore" :loading="loadingMore" @click="loadMore">加载更多高校</el-button>
        <span v-else-if="schoolList.length" class="no-more">— 已经到底啦 —</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiShopTypes, apiShopByType, apiShopByName } from '../api'
import CampusCard from '../components/CampusCard.vue'

const route = useRoute()
const router = useRouter()

const types = ref([])
const activeType = ref('all')
const schoolList = ref([])
const current = ref(1)
const hasMore = ref(false)
const loadingMore = ref(false)

const searchList = ref([])
const searchMode = computed(() => !!route.query.name)

const typeNameOf = (tid) => types.value.find((t) => t.id === tid)?.name || ''

async function loadTypes() {
  types.value = await apiShopTypes()
}

async function loadSchools(reset = false) {
  if (reset) {
    current.value = 1
    schoolList.value = []
  }
  loadingMore.value = true
  try {
    if (activeType.value === 'all') {
      // "全部":并发拉取所有类别第一页并按 id 去重合并(每类至多 5 条)
      const results = await Promise.all(
        types.value.map((t) =>
          apiShopByType({ typeId: t.id, current: 1 }).catch(() => [])
        )
      )
      const seen = new Set()
      const merged = []
      results.forEach((list) =>
        (list || []).forEach((s) => {
          if (!seen.has(s.id)) {
            seen.add(s.id)
            merged.push(s)
          }
        })
      )
      schoolList.value = merged
      hasMore.value = false
    } else {
      const r = await apiShopByType({
        typeId: activeType.value,
        current: current.value
      })
      const list = r || []
      schoolList.value = reset ? list : [...schoolList.value, ...list]
      hasMore.value = list.length >= 5
    }
  } finally {
    loadingMore.value = false
  }
}

function loadMore() {
  current.value += 1
  loadSchools()
}

function onSelectType(index) {
  activeType.value = String(index)
  loadSchools(true)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function doSearch() {
  searchList.value = await apiShopByName({ name: route.query.name, current: 1 })
}

function exitSearch() {
  router.push({ path: '/', query: {} })
}

onMounted(async () => {
  await loadTypes()
  if (searchMode.value) doSearch()
  else loadSchools(true)
})

watch(searchMode, (m) => (m ? doSearch() : loadSchools(true)))
watch(() => route.query.name, () => searchMode.value && doSearch())
</script>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #1e1b4b 0%, #4f46e5 70%, #7c3aed 100%);
  border-radius: 16px;
  padding: 26px 32px;
  color: #fff;
  margin-bottom: 22px;
  box-shadow: 0 14px 34px rgba(79, 70, 229, 0.3);
}
.hero-text h2 {
  margin: 0 0 6px;
  font-size: 24px;
}
.hero-text p {
  margin: 0;
  opacity: 0.85;
  font-size: 14px;
}
.section-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 6px 0 12px;
}
.section-bar h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-main);
}
.bar-sub {
  font-size: 12px;
  color: var(--text-sub);
}
.type-menu {
  border-bottom: none;
  margin-bottom: 16px;
}
.type-menu :deep(.el-menu-item.is-active) {
  color: var(--brand);
  font-weight: 600;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
  margin-bottom: 20px;
}
.load-more {
  text-align: center;
  padding: 10px 0 24px;
}
.no-more {
  color: #c0c4cc;
  font-size: 13px;
}
@media (max-width: 1080px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 820px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>