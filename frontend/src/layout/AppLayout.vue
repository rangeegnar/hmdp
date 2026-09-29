<template>
  <div class="layout">
    <TopNav />
    <main class="layout-main">
      <router-view />
    </main>
    <footer class="layout-footer">
      TokenMall · 校园 AI 算力商城 — 面向全体大学生的大模型 Token 购买 / 秒杀平台(教学演示)
    </footer>
  </div>
</template>

<script setup>
import TopNav from '../components/TopNav.vue'
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.layout-main {
  flex: 1;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px;
}
.layout-footer {
  text-align: center;
  padding: 18px;
  color: #aab;
  font-size: 12px;
  border-top: 1px solid #e5e7ff;
}
</style>