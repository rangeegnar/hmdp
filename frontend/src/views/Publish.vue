<template>
  <div class="publish">
    <div class="pub-card">
      <h2>✍️ 发布 AI 灵感</h2>
      <p class="sub">分享你的大模型使用技巧 / 提示词 / 开源项目,和全国大学生一起进步</p>

      <el-form label-position="top" class="pub-form">
        <el-form-item label="关联高校(算力资源节点)" required>
          <el-select v-model="form.shopId" filterable placeholder="选择你的高校" style="width: 100%">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="一句话说清你的灵感" />
        </el-form-item>

        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="展开讲讲你的思路 / 踩坑 / 成果…"
          />
        </el-form-item>

        <el-form-item label="配图(最多 9 张,单张 ≤5MB)">
          <div class="img-area">
            <!-- 已上传缩略图 -->
            <div v-for="(img, i) in images" :key="i" class="img-cell">
              <el-image :src="resolveImg(img)" fit="cover" class="img-preview" :preview-src-list="images.map(resolveImg)" :initial-index="i" />
              <el-button class="img-del" circle size="small" type="danger" @click="removeAt(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>

            <!-- 上传按钮 -->
            <el-upload
              :http-request="uploadImg"
              :show-file-list="false"
              accept="image/*"
              :before-upload="beforeUpload"
              :disabled="images.length >= 9 || uploading"
              class="upload-btn"
            >
              <el-icon class="plus"><Plus /></el-icon>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="submit">发 布</el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiShopTypes, apiShopByType, apiUploadImage, apiDeleteImage, apiSaveBlog } from '../api'
import { resolveImg } from '../utils/format'

const router = useRouter()
const schools = ref([])
const form = reactive({ shopId: null, title: '', content: '' })
const images = ref([])
const uploading = ref(false)
const submitting = ref(false)

async function loadSchools() {
  const types = (await apiShopTypes()) || []
  let list = []
  for (const t of types) {
    const r = (await apiShopByType({ typeId: t.id, current: 1 }).catch(() => [])) || []
    list = list.concat(r)
  }
  schools.value = list
}

function beforeUpload(file) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return false
  }
  return true
}

async function uploadImg({ file }) {
  if (images.value.length >= 9) return
  uploading.value = true
  try {
    const path = await apiUploadImage(file)
    images.value.push(path)
    ElMessage.success('上传成功')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    uploading.value = false
  }
}

async function removeAt(i) {
  const path = images.value[i]
  if (path) {
    try {
      await apiDeleteImage(path)
    } catch (e) {
      /* 忽略失败 */
    }
  }
  images.value.splice(i, 1)
}

async function submit() {
  if (!form.shopId) return ElMessage.warning('请选择关联高校')
  if (!form.title.trim()) return ElMessage.warning('请填写标题')
  if (!form.content.trim()) return ElMessage.warning('请填写正文')

  submitting.value = true
  try {
    const id = await apiSaveBlog({
      shopId: form.shopId,
      title: form.title,
      content: form.content,
      images: images.value.join(',')
    })
    ElMessage.success('发布成功!🎉')
    router.push(`/blog/${id}`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(loadSchools)
</script>

<style scoped>
.pub-card {
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #eef0ff;
  box-shadow: var(--shadow);
  max-width: 760px;
  margin: 0 auto;
  padding: 28px 32px;
}
.pub-card h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.pub-card .sub {
  margin: 0 0 20px;
  color: var(--text-sub);
  font-size: 13px;
}
.img-area {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.img-cell {
  position: relative;
  width: 100px;
  height: 100px;
}
.img-preview {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}
.img-del {
  position: absolute;
  top: -8px;
  right: -8px;
}
.upload-btn :deep(.el-upload) {
  width: 100px;
  height: 100px;
  border: 1px dashed #c7cbff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--brand);
}
.plus {
  font-size: 22px;
}
</style>