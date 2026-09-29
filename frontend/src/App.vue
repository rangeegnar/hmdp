<template>
  <router-view />
</template>

<style>
/* 全局基础样式 */
html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
  background: #f5f7ff;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
}
* {
  box-sizing: border-box;
}
a {
  text-decoration: none;
  color: inherit;
}
/* 主色变量,方便各组件引用 */
:root {
  --brand: #4f46e5;
  --brand-light: #6d6af7;
  --brand-bg: #eef0ff;
  --accent: #06b6d4;
  --warn: #f59e0b;
  --money: #f43f5e;
  --text-main: #1f2337;
  --text-sub: #6b7280;
  --radius: 12px;
  --shadow: 0 6px 20px rgba(79, 70, 229, 0.08);
}
/* 细滚动条 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-thumb {
  background: #c7cbff;
  border-radius: 3px;
}
</style>