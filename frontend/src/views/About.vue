<template>
  <div class="about">
    <div class="about-card">
      <h1>🎓 TokenMall · 校园 AI 算力商城</h1>
      <p class="tag">面向全体大学生的大模型 Token 购买 / 秒杀平台(前后端全链路教学演示)</p>

      <el-divider />

      <section>
        <h3>🌈 业务场景</h3>
        <p>
          把经典的「本地生活点评」业务翻新为大模型时代的「算力交易」:每一所高校是一个<b>算力资源节点</b>(shop),
          全国大学生(user)可以在各高校购买限量的大模型 <b>Token 套餐</b>(voucher),
          并参与有库存、倒计时、一人一单约束的 <b>Token 秒杀</b>(seckill)。
          登录支持多端在线、设备管理、强制全端下线。
        </p>
      </section>

      <section>
        <h3>🧠 技术看点</h3>
        <el-table :data="items" border style="margin-top: 10px">
          <el-table-column prop="k" label="技术点" width="180" />
          <el-table-column prop="v" label="实现说明" />
        </el-table>
      </section>

      <section class="env">
        <h3>🔧 环境说明</h3>
        <p>独立数据库 <b>college_token</b>(未污染原 hmdp 库)· Redis 缓存/库存/登录态全程参与 · RabbitMQ 异步落库</p>
        <p>后端 <code>localhost:8081</code> · 前端 <code>localhost:5173</code>(Vite 代理转发)</p>
      </section>
    </div>
  </div>
</template>

<script setup>
const items = [
  { k: '缓存防穿透', v: '缓存空对象 + Redisson 布隆过滤器拦截 → 商铺详情' },
  { k: 'Token 登录', v: 'Redis 存 UserDTO,authorization 头鉴权,多设备 + 滑窗续期' },
  { k: '多端管控', v: 'Lua 原子维护在线设备集合,登录超量自动踢出,支持指定/全端下线' },
  { k: '秒杀限购', v: 'Lua 校验库存+一人一单,Redis 扣减,失败立即返回' },
  { k: '异步落库', v: '下单消息 → RabbitMQ → 监听器创建订单(Stream 队列)' },
  { k: '点赞/签到', v: 'Redis ZSet 点赞去重、BitMap 签到与连续天数统计' },
  { k: '关注流', v: 'Feed 推流:TYPE 关注人发布 → ZSet 收件箱,滚动分页(游标 lastId/offset)' }
]
</script>

<style scoped>
.about-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  padding: 32px;
  max-width: 860px;
  margin: 0 auto;
}
.about-card h1 {
  margin: 0 0 6px;
  font-size: 26px;
}
.tag {
  color: var(--text-sub);
  font-size: 13px;
  margin: 0;
}
.about-card h3 {
  font-size: 16px;
  margin: 8px 0;
}
section p {
  font-size: 14px;
  line-height: 1.9;
  color: #444;
}
.env code {
  background: var(--brand-bg);
  color: var(--brand);
  padding: 2px 8px;
  border-radius: 6px;
}
</style>