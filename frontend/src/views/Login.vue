<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="brand">
        <div class="logo">🎓</div>
        <h1>TokenMall</h1>
        <p>校园 AI 算力商城 · 面向全体大学生</p>
        <p class="slogan">一台终端,畅享大模型算力。高校聚合,秒杀抢购。</p>
      </div>

      <el-tabs v-model="tab" class="login-tabs" stretch>
        <!-- 验证码登录 -->
        <el-tab-pane label="验证码登录" name="code">
          <el-form label-position="top" @submit.prevent>
            <el-form-item>
              <el-input v-model="phone" placeholder="手机号" size="large" :prefix-icon="IconsCellphone">
                <template #append>
                  <el-button :disabled="countdown > 0" @click="sendCode">
                    {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-input v-model="code" placeholder="短信验证码(见后端控制台输出)" size="large" :prefix-icon="IconsMessage" />
            </el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="loginByCode">
              登录 / 注册
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- 密码登录 -->
        <el-tab-pane label="密码登录" name="password">
          <el-form label-position="top" @submit.prevent>
            <el-form-item>
              <el-input v-model="passwordPhone" placeholder="手机号" size="large" :prefix-icon="IconsCellphone" />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="password"
                type="password"
                show-password
                placeholder="密码"
                size="large"
                :prefix-icon="IconsLock"
              />
            </el-form-item>
            <el-button type="primary" size="large" class="submit" :loading="loading" @click="loginByPassword">
              密码登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p class="tip">🕹️ 验证码不会真的下发短信,请在后端控制台日志中查看</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cellphone, Message, Lock } from '@element-plus/icons-vue'
import { apiSendCode, apiLoginByCode, apiLoginByPassword } from '../api'
import { useUserStore } from '../stores/user'

const IconsCellphone = Cellphone
const IconsMessage = Message
const IconsLock = Lock

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref('code')
const phone = ref('')
const code = ref('')
const passwordPhone = ref('')
const password = ref('')
const countdown = ref(0)
const loading = ref(false)

let timer = null

function goHome() {
  const redirect = route.query.redirect
  router.push(redirect || '/')
}

async function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
  try {
    await apiSendCode(phone.value)
    ElMessage.success('验证码已发送,请查看后端日志')
  } catch (e) {
    clearInterval(timer)
    countdown.value = 0
  }
}

async function loginByCode() {
  loading.value = true
  try {
    const token = await apiLoginByCode({ phone: phone.value, code: code.value })
    userStore.setToken(token)
    userStore.fetchMe()
    ElMessage.success('登录成功')
    goHome()
  } catch (e) {
    /* 错误已在拦截器提示 */
  } finally {
    loading.value = false
  }
}

async function loginByPassword() {
  loading.value = true
  try {
    const token = await apiLoginByPassword({ phone: passwordPhone.value, password: password.value })
    userStore.setToken(token)
    userStore.fetchMe()
    ElMessage.success('登录成功')
    goHome()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(800px 400px at 10% 10%, rgba(124, 58, 237, 0.18), transparent 60%),
    radial-gradient(700px 380px at 90% 90%, rgba(6, 182, 212, 0.16), transparent 60%),
    linear-gradient(135deg, #eef0ff, #f8f9ff);
}
.login-panel {
  width: 420px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 24px 60px rgba(79, 70, 229, 0.16);
  padding: 36px 40px 28px;
}
.brand {
  text-align: center;
  margin-bottom: 20px;
}
.logo {
  font-size: 46px;
}
.brand h1 {
  margin: 6px 0 4px;
  font-size: 28px;
  background: linear-gradient(90deg, #4f46e5, #7c3aed);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.brand p {
  margin: 0;
  color: var(--text-sub);
  font-size: 13px;
}
.slogan {
  margin-top: 6px !important;
  font-size: 12px !important;
  color: #aab !important;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.tip {
  margin: 16px 0 0;
  text-align: center;
  font-size: 12px;
  color: #aab;
}
</style>