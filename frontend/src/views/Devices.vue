<template>
  <div class="devices">
    <el-page-header @back="$router.back()" content="在线设备管理" style="margin-bottom: 16px" />

    <div class="dev-card">
      <div class="dev-head">
        <h3>🖥️ 当前账号在线设备({{ devices.length }})</h3>
        <p>支持多端同时登录;踢下线后对应设备 Token 立即失效</p>
      </div>

      <el-table :data="devices" v-loading="loading" empty-text="暂无在线设备">
        <el-table-column label="设备" min-width="200">
          <template #default="{ row }">
            <div class="dev-cell">
              <el-icon :size="22" style="color: #4f46e5"><Monitor /></el-icon>
              <div>
                <div class="dev-name">
                  Token 设备 {{ tokenText(row.token) }}
                  <el-tag v-if="isCurrent(row.token)" size="small" type="success" style="margin-left: 6px">当前设备</el-tag>
                </div>
                <div class="dev-time">登录时间:{{ formatTime(row.loginTime, true) }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="{ row }">
            <el-button type="danger" plain size="small" @click="kick(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="kick-all">
        <el-button type="danger" plain @click="kickAll">🔓 强制当前账号全端下线</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiDevices, apiKickDevice, apiKickAll } from '../api'
import { useUserStore } from '../stores/user'
import { formatTime } from '../utils/format'

const router = useRouter()
const userStore = useUserStore()
const devices = ref([])
const loading = ref(false)

const tokenText = (t) => `${String(t).slice(0, 6)}…${String(t).slice(-6)}`
const isCurrent = (t) => t === userStore.token || t === 'Bearer ' + userStore.token

async function load() {
  loading.value = true
  try {
    devices.value = (await apiDevices()) || []
  } finally {
    loading.value = false
  }
}

async function kick(row) {
  try {
    await ElMessageBox.confirm(`确定下线设备 ${tokenText(row.token)} 吗?`, '下线设备', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await apiKickDevice(row.token)
  } catch (e) {
    return
  }
  if (isCurrent(row.token)) {
    // 下线的是当前设备:强制重新登录
    userStore.clear()
    ElMessage.success('当前设备已下线')
    router.push('/login')
    return
  }
  ElMessage.success('设备已下线')
  load()
}

async function kickAll() {
  try {
    await ElMessageBox.confirm('将强制当前账号所有设备下线,包括本机,确定继续?', '全端下线', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await apiKickAll()
  } catch (e) {
    return
  }
  userStore.clear()
  ElMessage.success('已全端下线')
  router.push('/login')
}

onMounted(load)
</script>

<style scoped>
.dev-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 24px;
  max-width: 860px;
  margin: 0 auto;
}
.dev-head h3 {
  margin: 0 0 6px;
  font-size: 17px;
}
.dev-head p {
  margin: 0 0 18px;
  font-size: 12px;
  color: var(--text-sub);
}
.dev-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dev-name {
  font-size: 14px;
  font-weight: 600;
}
.dev-time {
  font-size: 12px;
  color: var(--text-sub);
}
.kick-all {
  margin-top: 18px;
  text-align: right;
}
</style>