<template>
  <div v-if="shop" class="shop-detail">
    <!-- 顶部返回 -->
    <el-page-header @back="$router.push('/')" content="高校 · 算力能量站" style="margin-bottom: 16px" />

    <div class="detail-grid">
      <!-- 左:高校信息卡 -->
      <aside class="info-card">
        <div class="cover" :style="coverStyle(shop.id)">
          <div class="cover-deco">AI</div>
          <span class="cover-name">{{ firstChar }}</span>
          <span class="type-tag">{{ typeName }}</span>
        </div>
        <h2 class="name">{{ shop.name }}</h2>
        <div class="desc">
          <p><el-icon><Location /></el-icon>{{ shop.address }}</p>
          <p><el-icon><Guide /></el-icon>商圈:{{ shop.area || '—' }}</p>
          <p><el-icon><Clock /></el-icon>算力池开放:{{ shop.openHours }}</p>
          <p><el-icon><Coordinate /></el-icon>坐标:{{ shop.x }},{{ shop.y }}</p>
        </div>
        <el-divider />
        <div class="stats">
          <div class="stat">
            <b>{{ shop.sold }}</b>
            <span>已购算力</span>
          </div>
          <div class="stat">
            <b>{{ (shop.score / 10).toFixed(1) }}</b>
            <span>满意度</span>
          </div>
          <div class="stat">
            <b>¥{{ shop.avgPrice }}</b>
            <span>月人均</span>
          </div>
        </div>
      </aside>

      <!-- 右:Token 套餐 -->
      <section class="voucher-panel">
        <div class="panel-head">
          <h3>⚡ {{ shop.name }} · Token 套餐</h3>
          <span class="hint">学生专属价 · 秒杀包在线实时下单</span>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="演示说明:仅「秒杀包」支持在线实时下单(走 Redis 库存 + 异步消息落库);普通套餐为展示,可在秒杀包内体验完整链路。"
          class="demo-tip"
        />

        <template v-if="vouchers.length">
          <VoucherCard
            v-for="v in vouchers"
            :key="v.id"
            :voucher="v"
            @buy="onBuy"
            @expired="loadVouchers"
          />
        </template>
        <el-empty v-else description="该高校暂无在售套餐" />
      </section>
    </div>
  </div>
  <el-skeleton v-else :rows="6" animated style="margin-top: 40px" />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiShop, apiVoucherList, apiShopTypes, apiSeckillVoucher } from '../api'
import { coverStyle, fen2yuan, formatToken } from '../utils/format'
import VoucherCard from '../components/VoucherCard.vue'

const route = useRoute()
const shop = ref(null)
const types = ref([])
const vouchers = ref([])

const firstChar = computed(() => (shop.value?.name || '校').slice(0, 1))
const typeName = computed(() => types.value.find((t) => t.id === shop.value?.typeId)?.name || '')

async function load() {
  const id = route.params.id
  try {
    shop.value = await apiShop(id)
  } catch (e) {
    ElMessage.error('高校不存在或已被下线')
    return
  }
  if (!types.value.length) types.value = (await apiShopTypes()) || []
  await loadVouchers()
}

async function loadVouchers() {
  const id = route.params.id
  vouchers.value = (await apiVoucherList(id)) || []
  // 秒杀包永远排前面
  vouchers.value.sort((a, b) => b.type - a.type)
}

async function onBuy(v) {
  if (v.type !== 1) {
    ElMessage.info('普通套餐仅展示,演示环境请体验秒杀包在线下单')
    return
  }
  try {
    await ElMessageBox.confirm(
      `<div style="font-size:13px;line-height:1.9">
        <p>套餐:<b>${v.title}</b></p>
        <p>获得:<b style="color:#4f46e5">${formatToken(v.actualValue)} Token</b></p>
        <p>秒杀价:<b style="color:#f43f5e;font-size:18px">¥${fen2yuan(v.payValue)}</b></p>
        <p>剩余库存:<b>${v.stock}</b> 份</p>
      </div>`,
      '确认秒杀下单',
      { dangerouslyUseHTMLString: true, type: 'warning', confirmButtonText: '确认秒杀', cancelButtonText: '再想想' }
    )
  } catch (e) {
    return
  }
  try {
    const orderId = await apiSeckillVoucher(v.id)
    ElMessage.success(`🎉 秒杀成功!订单号 ${orderId} 已提交,正在异步创建订单`)
  } catch (e) {
    /* 拦截器已提示业务失败(库存不足/重复下单/未开始/已结束等) */
  }
}

onMounted(load)
</script>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  align-items: start;
}
.info-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  overflow: hidden;
  padding-bottom: 16px;
  position: sticky;
  top: 84px;
}
.cover {
  position: relative;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  overflow: hidden;
}
.cover-deco {
  position: absolute;
  right: 14px;
  top: 8px;
  font-size: 34px;
  font-weight: 900;
  opacity: 0.25;
}
.cover-name {
  font-size: 62px;
  font-weight: 800;
  letter-spacing: 4px;
  text-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}
.type-tag {
  position: absolute;
  left: 12px;
  bottom: 10px;
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(4px);
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
}
.name {
  margin: 16px 20px 4px;
  font-size: 20px;
}
.desc {
  margin: 10px 20px 0;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 2;
}
.desc p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}
.stats {
  display: flex;
  justify-content: space-around;
  margin-top: 8px;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat b {
  font-size: 18px;
  color: var(--brand);
}
.stat span {
  font-size: 11px;
  color: var(--text-sub);
}
.voucher-panel {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 20px;
}
.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-head h3 {
  margin: 0;
  font-size: 18px;
}
.hint {
  font-size: 12px;
  color: var(--text-sub);
}
.demo-tip {
  margin-bottom: 16px;
  --el-alert-bg-color: #f0f3ff;
  --el-alert-border-color: #dbe3ff;
}
</style>