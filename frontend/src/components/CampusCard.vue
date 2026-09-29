<template>
  <div class="campus-card" @click="$router.push(`/shop/${shop.id}`)">
    <!-- 封面:品牌渐变 + 校名首字 -->
    <div class="cover" :style="coverStyle(shop.id)">
      <div class="cover-deco">AI</div>
      <div class="cover-name">{{ firstChar }}</div>
      <span class="type-tag">{{ typeName }}</span>
    </div>
    <div class="body">
      <div class="title-row">
        <h3 class="name">{{ shop.name }}</h3>
        <span class="score"><el-icon><Star /></el-icon>{{ (shop.score / 10).toFixed(1) }}</span>
      </div>
      <p class="area">
        <el-icon><Location /></el-icon>
        {{ shop.area || shop.address }}
      </p>
      <div class="meta">
        <div class="meta-item">
          <span class="meta-label">月人均消费</span>
          <span class="meta-value"><b>¥{{ shop.avgPrice }}</b></span>
        </div>
        <div class="meta-item">
          <span class="meta-label">已购算力</span>
          <span class="meta-value"><b>{{ shop.sold }}</b> 人</span>
        </div>
      </div>
      <div class="footer">
        <span class="open">{{ shop.openHours }}</span>
        <el-button type="primary" round size="small" @click.stop="$router.push(`/shop/${shop.id}`)">
          进入校园 → 选购 Token
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { coverStyle } from '../utils/format'

const props = defineProps({
  shop: { type: Object, required: true },
  typeName: { type: String, default: '' }
})

const firstChar = computed(() => (props.shop.name || '校').slice(0, 1))
</script>

<style scoped>
.campus-card {
  background: #fff;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.campus-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(79, 70, 229, 0.16);
}
.cover {
  position: relative;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  overflow: hidden;
}
.cover-deco {
  position: absolute;
  right: 14px;
  top: 10px;
  font-size: 30px;
  font-weight: 900;
  opacity: 0.25;
}
.cover-name {
  font-size: 56px;
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
.body {
  padding: 14px 16px 16px;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-main);
}
.score {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--warn);
  font-size: 13px;
  font-weight: 600;
  background: #fff7e6;
  padding: 2px 8px;
  border-radius: 10px;
}
.area {
  margin: 6px 0 12px;
  font-size: 12px;
  color: var(--text-sub);
  display: flex;
  align-items: center;
  gap: 3px;
}
.meta {
  display: flex;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px dashed #eef0ff;
  border-bottom: 1px dashed #eef0ff;
}
.meta-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.meta-label {
  font-size: 11px;
  color: var(--text-sub);
}
.meta-value {
  font-size: 13px;
  color: var(--text-main);
}
.meta-value b {
  color: var(--brand);
  font-size: 15px;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.open {
  font-size: 12px;
  color: var(--accent);
}
</style>