/**
 * 展示层格式化工具:金额、Token 数量、时间、图片路径
 */

/** 分 -> 元,返回字符串,如 2500 -> "25.00" */
export function fen2yuan(fen) {
  if (fen == null) return '-'
  return (fen / 100).toFixed(2)
}

/** Token 数字量 -> 可读文本:1200000 -> "120万" */
export function formatToken(num) {
  if (num == null) return '-'
  const n = Number(num)
  if (n >= 100000000) return (n / 100000000).toFixed(1).replace(/\.0$/, '') + '亿'
  if (n >= 10000) return (n / 10000).toFixed(0) + '万'
  return String(n)
}

/**
 * 时间戳/时间串 -> 展示文本。
 * loginTime 支持秒(10位)或毫秒(13位)时间戳,也支持 ISO 时间串。
 */
export function formatTime(value, withTime = false) {
  if (!value) return '-'
  let d
  if (typeof value === 'number' || /^\d+$/.test(String(value))) {
    const n = Number(value)
    d = new Date(n < 1e12 ? n * 1000 : n)
  } else {
    d = new Date(value)
  }
  if (isNaN(d.getTime())) return String(value)
  const p = (x) => String(x).padStart(2, '0')
  const date = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
  return withTime ? `${date} ${p(d.getHours())}:${p(d.getMinutes())}` : date
}

/**
 * 图片路径 -> 可访问 URL。
 * 后端上传返回 /blogs/...;库里演示数据 /imgs/...;统一补前缀走 vite proxy -> 后端静态资源。
 */
export function resolveImg(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return url.startsWith('/blogs') ? '/imgs' + url : url
}

/** 多图逗号分隔 -> 数组 */
export function splitImages(images) {
  if (!images) return []
  return String(images).split(',').filter(Boolean)
}

/** 高校序号 -> 品牌渐变(封面) */
const PALETTE = [
  'linear-gradient(135deg,#4f46e5 0%,#7c3aed 100%)',
  'linear-gradient(135deg,#0ea5e9 0%,#2563eb 100%)',
  'linear-gradient(135deg,#06b6d4 0%,#0d9488 100%)',
  'linear-gradient(135deg,#f59e0b 0%,#ef4444 100%)',
  'linear-gradient(135deg,#10b981 0%,#059669 100%)',
  'linear-gradient(135deg,#8b5cf6 0%,#d946ef 100%)',
  'linear-gradient(135deg,#f43f5e 0%,#fb7185 100%)',
  'linear-gradient(135deg,#6366f1 0%,#8b5cf6 100%)'
]
export function coverStyle(id) {
  return { background: PALETTE[(Number(id) || 0) % PALETTE.length] }
}