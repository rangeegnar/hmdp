<template>
  <div class="voucher-card" :class="{ seckill: isSeckill }">
    <!-- 秒杀角标 -->
    <div v-if="isSeckill" class="seckill-badge">⚡ 秒杀</div>

    <div class="left">
      <div class="token-num">
        <span class="num">{{ formatToken(voucher.actualValue) }}</span>
        <span class="unit">Token</span>
      </div>
      <div v-if="isSeckill" class="origin-price">限量 · 手慢无</div>
    </div>

    <div class="mid">
      <h4 class="title">{{ voucher.title }}</h4>
      <p class="sub">{{ voucher.subTitle }}</p>
      <div v-if="isSeckill" class="stock">
        剩余库存 <b>{{ voucher.stock }}</b> 份 ·
        <CountDown
          :end-time="voucher.endTime"
          label="距结束"
          small
          @end="$emit('expired')"
        />
      </div>
      <div v-else class="stock">随时可购 · 秒到账</div>
    </div>

    <div class="right">
      <div class="price">
        <span class="cur">¥</span>{{ isSeckill ? seckillPrice : fen2yuan(voucher.payValue) }}
      </div>
      <el-button :type="isSeckill ? 'danger' : 'primary'" round @click="$emit('buy', voucher)">
        {{ buttonText }}
      </el-button>
    </div>

    <el-tooltip v-if="isSeckill" placement="top" :content="voucher.rules || '规则见套餐说明'">
      <span class="rule-tip">规则</span>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fen2yuan, formatToken } from '../utils/format'
import CountDown from './CountDown.vue'

const props = defineProps({
  voucher: { type: Object, required: true }
})
defineEmits(['buy', 'expired'])

const isSeckill = computed(() => props.voucher.type === 1)
/** 秒杀价沿用 pay_value(秒杀包 pay_value 即秒杀价) */
const seckillPrice = computed(() => fen2yuan(props.voucher.payValue))
const buttonText = computed(() => (isSeckill.value ? '立即秒杀' : '套餐展示'))
</script>

<style scoped>
.voucher-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #eef0ff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 14px;
  transition: transform 0.2s;
}
.voucher-card:hover {
  transform: translateY(-2px);
}
.voucher-card.seckill {
  border-color: #ffd8e0;
  background: linear-gradient(90deg, #fff5f6, #fff);
}
.seckill-badge {
  position: absolute;
  left: 0;
  top: 14px;
  background: linear-gradient(90deg, #f43f5e, #fb7185);
  color: #fff;
  font-size: 12px;
  padding: 3px 12px 3px 8px;
  border-radius: 0 12px 12px 0;
  font-weight: 600;
}
.left {
  width: 140px;
  text-align: center;
  border-right: 2px dashed #eef0ff;
  padding-right: 20px;
  flex-shrink: 0;
}
.token-num .num {
  font-size: 30px;
  font-weight: 800;
  color: var(--brand);
}
.token-num .unit {
  font-size: 13px;
  color: var(--text-sub);
  margin-left: 2px;
}
.origin-price {
  margin-top: 4px;
  color: #f43f5e;
  font-size: 12px;
}
.mid {
  flex: 1;
  min-width: 0;
}
.title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}
.sub {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--text-sub);
}
.stock {
  font-size: 12px;
  color: var(--text-sub);
}
.stock b {
  color: var(--money);
  font-size: 14px;
}
.right {
  text-align: right;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.price {
  font-size: 26px;
  font-weight: 800;
  color: var(--money);
}
.price .cur {
  font-size: 15px;
}
.rule-tip {
  position: absolute;
  right: 12px;
  bottom: 10px;
  font-size: 11px;
  color: #c0c4cc;
  cursor: help;
}
</style>