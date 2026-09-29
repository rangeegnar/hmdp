<template>
  <div class="blog-card" @click="$router.push(`/blog/${blog.id}`)">
    <div class="head">
      <el-avatar :size="36" class="avatar">{{ authorChar }}</el-avatar>
      <div class="author">
        <span class="name">{{ blog.name || '同学' }}</span>
        <span class="time">{{ formatTime(blog.createTime, true) }}</span>
      </div>
      <span class="is-like-tag" v-if="blog.isLike">已赞</span>
    </div>

    <h3 class="title">{{ blog.title }}</h3>
    <p class="content" v-if="!images.length">{{ blog.content }}</p>

    <div v-if="images.length" class="gallery" :class="{ many: images.length > 1 }">
      <el-image
        v-for="(img, i) in images.slice(0, 3)"
        :key="i"
        :src="resolveImg(img)"
        fit="cover"
        :preview-src-list="images.slice(0, 6).map(resolveImg)"
        class="gallery-img"
        @click.stop
      />
      <span v-if="images.length > 3" class="more">+{{ images.length - 3 }}</span>
    </div>

    <div class="foot">
      <span class="stat"><el-icon><Star /></el-icon>{{ blog.liked ?? 0 }}</span>
      <span class="stat"><el-icon><ChatDotRound /></el-icon>{{ blog.comments ?? 0 }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatTime, resolveImg, splitImages } from '../utils/format'

const props = defineProps({
  blog: { type: Object, required: true }
})

const images = computed(() => splitImages(props.blog.images))
const authorChar = computed(() => (props.blog.name || '同').slice(0, 1))
</script>

<style scoped>
.blog-card {
  background: #fff;
  border: 1px solid #eef0ff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.blog-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 26px rgba(79, 70, 229, 0.14);
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-weight: 600;
}
.author {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}
.time {
  font-size: 11px;
  color: #a0a5bb;
}
.is-like-tag {
  margin-left: auto;
  font-size: 11px;
  color: var(--money);
  background: #fff0f3;
  padding: 2px 8px;
  border-radius: 10px;
}
.title {
  margin: 12px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.content {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.gallery {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 10px;
}
.gallery.many {
  height: 120px;
}
.gallery-img {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}
.more {
  position: absolute;
  right: 8px;
  bottom: 8px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}
.foot {
  display: flex;
  gap: 16px;
  margin-top: 12px;
}
.stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-sub);
}
</style>