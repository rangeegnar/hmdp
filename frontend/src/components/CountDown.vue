<template>
  <div class="count-down" :class="{ small }">
    <span v-if="label" class="cd-label">{{ label }}</span>
    <template v-if="!ended">
      <span class="cd-block">{{ hh }}</span>:<span class="cd-block">{{ mm }}</span
      >:<span class="cd-block">{{ ss }}</span>
    </template>
    <span v-else class="cd-ended">已结束</span>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 倒计时:给定结束时间(ISO 串或时间戳),每秒更新。
 * 结束后触发 end 事件。
 */
const props = defineProps({
  endTime: { type: [String, Number], required: true },
  label: { type: String, default: '距结束' },
  small: { type: Boolean, default: false }
})
const emit = defineEmits(['end'])

const now = ref(Date.now())
let timer = null

const endMs = computed(() => {
  const v = props.endTime
  if (!v) return 0
  if (typeof v === 'number' || /^\d+$/.test(String(v))) {
    const n = Number(v)
    return n < 1e12 ? n * 1000 : n
  }
  return new Date(v).getTime()
})
const left = computed(() => Math.max(0, endMs.value - now.value))
const ended = computed(() => left.value <= 0)
const hh = computed(() => String(Math.floor(left.value / 3600000)).padStart(2, '0'))
const mm = computed(() => String(Math.floor((left.value % 3600000) / 60000)).padStart(2, '0'))
const ss = computed(() => String(Math.floor((left.value % 60000) / 1000)).padStart(2, '0'))

onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => timer && clearInterval(timer))
</script>

<style scoped>
.count-down {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.cd-label {
  color: var(--text-sub);
  margin-right: 2px;
}
.cd-block {
  display: inline-block;
  min-width: 22px;
  text-align: center;
  padding: 2px 4px;
  border-radius: 4px;
  background: var(--brand);
  color: #fff;
  font-weight: 700;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.small .cd-block {
  min-width: 18px;
  padding: 1px 3px;
  font-size: 11px;
  background: rgba(79, 70, 229, 0.12);
  color: var(--brand);
}
.cd-ended {
  color: var(--text-sub);
}
</style>