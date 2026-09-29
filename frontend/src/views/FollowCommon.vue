<template>
  <div class="follow-common">
    <el-page-header @back="$router.back()" content="共同关注" style="margin-bottom: 16px" />

    <div class="fc-card">
      <h3>🤝 我与 TA 共同关注的伙伴</h3>
      <p class="fc-sub">你们有 {{ list.length }} 位共同关注的伙伴</p>

      <div v-loading="loading" class="fc-list">
        <div v-for="u in list" :key="u.id" class="fc-item" @click="$router.push(`/user/${u.id}`)">
          <el-avatar :size="46" class="avatar">{{ (u.nickName || '·').slice(0, 1) }}</el-avatar>
          <span class="fc-name">{{ u.nickName }}</span>
          <el-button round size="small" @click.stop="$router.push(`/user/${u.id}`)">看看 TA</el-button>
        </div>
        <el-empty v-if="!list.length && !loading" description="暂无共同关注的伙伴" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFollowCommon } from '../api'

const route = useRoute()
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = (await apiFollowCommon(route.params.id)) || []
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.fc-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 24px;
  max-width: 720px;
  margin: 0 auto;
}
.fc-card h3 {
  margin: 0 0 4px;
  font-size: 17px;
}
.fc-sub {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--text-sub);
}
.fc-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-bottom: 1px solid #f2f3ff;
  cursor: pointer;
  transition: background 0.2s;
  border-radius: 8px;
}
.fc-item:hover {
  background: var(--brand-bg);
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-weight: 600;
}
.fc-name {
  flex: 1;
  font-weight: 600;
}
</style>