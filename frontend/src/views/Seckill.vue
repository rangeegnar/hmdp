<template>
  <div class="seckill">
    <div class="sec-head">
      <div>
        <h2>⚡ 分会场 · 限时秒杀</h2>
        <p>所有在售 Token 秒杀包实时库存,Redis 减库存 + 异步落库,手慢无</p>
      </div>
    </div>

    <template v-if="seckills.length">
      <section v-for="g in grouped" :key="g.shopId" class="group">
        <h3 class="group-title">
          🏫 {{ g.shopName }} 秒杀专场
          <router-link class="more" :to="`/shop/${g.shopId}`">进入校园 →</router-link>
        </h3>
        <div class="sec-grid">
          <div v-for="v in g.list" :key="v.id" class="sec-card" :class="{ ended: isEnded(v) }">
            <div class="sec-badge">⚡ 限量秒杀</div>
            <div class="sec-token">
              <span class="num">{{ formatToken(v.actualValue) }}</span>
              <span class="unit">Token</span>
            </div>
            <h4>{{ v.title }}</h4>
            <p class="sub">{{ v.sub_title }}</p>

            <div class="time-row">
              <CountDown :end-time="v.endTime" label="距结束" :small="true" />
            </div>

            <div class="buy-row">
              <div class="price">
                <span class="cur">¥</span>{{ fen2yuan(v.payValue) }}
                <span class="stock">剩 {{ v.stock }} 份</span>
              </div>
              <el-button type="danger" round :disabled="isEnded(v)" :loading="buyingId === v.id" @click="doBuy(v)">
                {{ isEnded(v) ? '已结束' : '抢购' }}
              </el-button>
            </div>
          </div>
        </div>
      </section>
      <el-empty v-if="!seckills.length" description="当前无进行中的秒杀" />
    </template>
    <el-skeleton v-else :rows="8" animated style="margin-top: 30px" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiShopTypes, apiShopByType, apiVoucherList, apiSeckillVoucher } from '../api'
import { fen2yuan, formatToken } from '../utils/format'
import CountDown from '../components/CountDown.vue'

const seckills = ref([])
const buyingId = ref(null)

// 拉全部高校的秒杀包
async function load() {
  const types = (await apiShopTypes()) || []
  let schools = []
  for (const t of types) {
    const list = (await apiShopByType({ typeId: t.id, current: 1 }).catch(() => [])) || []
    schools = schools.concat(list)
  }
  const seen = new Set()
  const uniq = schools.filter((s) => !seen.has(s.id) && seen.add(s.id))

  const all = []
  for (const s of uniq) {
    const vs = (await apiVoucherList(s.id).catch(() => [])) || []
    vs.filter((v) => v.type === 1).forEach((v) => all.push({ ...v, shopName: s.name, shopId: s.id }))
  }
  seckills.value = all
}

const grouped = computed(() => {
  const map = new Map()
  seckills.value.forEach((v) => {
    if (!map.has(v.shopId)) map.set(v.shopId, { shopId: v.shopId, shopName: v.shopName, list: [] })
    map.get(v.shopId).list.push(v)
  })
  return [...map.values()]
})

function isEnded(v) {
  const end = new Date(v.endTime).getTime()
  return Date.now() > end
}

async function doBuy(v) {
  if (isEnded(v)) return
  try {
    await ElMessageBox.confirm(
      `<div style="font-size:13px;line-height:1.9">
        <p>套餐:<b>${v.title}</b></p>
        <p>获得:<b style="color:#4f46e5">${formatToken(v.actualValue)} Token</b></p>
        <p>秒杀价:<b style="color:#f43f5e;font-size:18px">¥${fen2yuan(v.payValue)}</b></p>
        <p>剩余库存:<b>${v.stock}</b> 份 · 每人限购 1 份</p>
      </div>`,
      '确认秒杀下单',
      { dangerouslyUseHTMLString: true, type: 'warning', confirmButtonText: '确认秒杀', cancelButtonText: '再想想' }
    )
  } catch (e) {
    return
  }
  buyingId.value = v.id
  try {
    const orderId = await apiSeckillVoucher(v.id)
    ElMessage.success(`🎉 秒杀成功!订单号 ${orderId} 已提交,正在异步创建订单`)
    v.stock = Math.max(0, v.stock - 1)
  } catch (e) {
    /* 拦截器已提示(库存不足/重复下单/未开始/已结束) */
  } finally {
    buyingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.sec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.sec-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
}
.sec-head p {
  margin: 0;
  color: var(--text-sub);
  font-size: 13px;
}
.group {
  margin-bottom: 28px;
}
.group-title {
  font-size: 17px;
  margin: 0 0 14px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.more {
  margin-left: auto;
  font-size: 12px;
  color: var(--brand);
}
.sec-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.sec-card {
  position: relative;
  background: #fff;
  border: 1px solid #ffdde5;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 20px 18px 18px;
  transition: transform 0.2s;
  overflow: hidden;
}
.sec-card:hover {
  transform: translateY(-3px);
}
.sec-card.ended {
  filter: grayscale(0.8);
  opacity: 0.7;
}
.sec-badge {
  position: absolute;
  left: 0;
  top: 0;
  background: linear-gradient(90deg, #f43f5e, #fb7185);
  color: #fff;
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 0 0 12px 0;
  font-weight: 600;
}
.sec-token {
  margin-top: 18px;
}
.sec-token .num {
  font-size: 34px;
  font-weight: 800;
  color: var(--brand);
}
.sec-token .unit {
  font-size: 13px;
  color: var(--text-sub);
  margin-left: 2px;
}
.sec-card h4 {
  margin: 8px 0 4px;
  font-size: 15px;
}
.sub {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--text-sub);
  min-height: 34px;
}
.time-row {
  margin-bottom: 12px;
}
.buy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.price {
  font-size: 24px;
  font-weight: 800;
  color: var(--money);
}
.price .cur {
  font-size: 14px;
}
.stock {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-sub);
}
@media (max-width: 1080px) {
  .sec-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 820px) {
  .sec-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>