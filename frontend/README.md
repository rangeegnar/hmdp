# 🎓 TokenMall · 校园 AI 算力商城(前端)

基于 **Vue 3 + Vite + Element Plus + Pinia + Axios** 构建的桌面 Web 前端,
对接后端 `hmdp`(黑马点评)改造项目,业务场景翻新为「高校 × 大学生 大模型 Token 购买/秒杀」。

## 快速开始

```bash
npm install
npm run dev        # -> http://localhost:5173 (已配置 Vite 代理到 localhost:8081)
```

## 前置条件

| 依赖 | 说明 |
|---|---|
| 后端 | 启动于 `localhost:8081`,数据源指向独立数据库 **college_token** |
| MySQL | 新库已由 `../build/college_token.sql` 初始化(不污染原 hmdp 库) |
| Redis | 登录态 / 商铺缓存 / 点赞 / 签到 / 秒杀库存 / 关注流,Redis 可随用随清(`FLUSHDB`) |
| RabbitMQ | 秒杀下单异步落库(如未启动,秒杀接口会报错) |

## 账号

验证码登录为主:输入任意 11 位手机号 → 获取验证码(不会真发短信,**验证码打印在后端控制台日志**,如 6 位数字)→ 登录。
预置大学生账号(1~5 都能登录,如手机号 `13686869696`)。

## 目录结构

```
src
├── api/            # axios 封装(request.js) + 全部接口定义(index.js)
├── components/     # TopNav / CampusCard / VoucherCard / CountDown / BlogCard
├── layout/         # AppLayout(顶栏 + 内容区 + 页脚)
├── router/         # 路由 + 登录守卫
├── stores/         # Pinia(user.js 登录态)
├── styles/         # 全局样式
├── utils/          # 格式化工具(金额/Token/时间/图片路径)+ 高校封面配色
└── views/          # Login/Home/ShopDetail/Seckill/Blog/BlogDetail/Publish/User/UserOther/Devices/FollowCommon/About
```

## 页面与接口对照

| 页面 | 路由 | 主要接口 |
|---|---|---|
| 登录 | `/login` | `POST /user/code`,`POST /user/login`,`POST /user/loginWithPassword` |
| 首页 | `/` | `GET /shop-type/list`,`GET /shop/of/type`,`GET /shop/of/name` |
| 高校详情 | `/shop/:id` | `GET /shop/{id}`,`GET /voucher/list/{shopId}` |
| 限时秒杀 | `/seckill` | `GET /voucher/list/{shopId}`(聚合秒杀包)、`POST /voucher-order/seckill/{id}` |
| 灵感广场 | `/blog` | `GET /blog/hot`, `GET /blog/of/follow`(游标滚动) |
| 灵感详情 | `/blog/:id` | `GET /blog/{id}`,`GET /blog/likes/{id}`,`PUT /blog/like/{id}` |
| 发布灵感 | `/publish` | `POST /upload/blog`,`DELETE /upload/blog/delete`,`POST /blog` |
| 我的 | `/user` | `GET /user/me`,`GET /user/info/{id}`,`POST /user/sign`,`GET /user/sign/count`,`GET /blog/of/me` |
| 他人主页 | `/user/:id` | `GET /user/{id}`,`GET /follow/*`,`GET /blog/of/user` |
| 设备管理 | `/devices` | `GET/POST /user/devices`,`POST /user/kickAll` |
| 共同关注 | `/follow/:id` | `GET /follow/common/{id}` |
| 关于 | `/about` | 静态场景说明 |

## 联调注意点

- 秒杀下单是 **Lua 校验 + RabbitMQ 异步落库**:前端收到订单号只代表"下单成功",订单真正生成在后端消息监听器。
- 秒杀包有 **库存(Redis)、时间窗、一人一单** 三重校验;重复下单会返回"不能重复下单"。
- **评论区无后端接口**(blog-comments 为空壳),页面以提示占位。
- 上传图片返回 `/blogs/...`,前端统一加 `/imgs` 前缀由后端静态映射提供访问(Vite 代理转发)。