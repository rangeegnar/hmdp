-- ============================================================
-- 高校 × 大模型 Token 购买/秒杀 场景初始化脚本
-- 基于原 hmdp.sql 表结构,业务数据整体翻新,供独立新库 college_token 使用
-- 运行方式: mysql -uroot -p college_token < college_token.sql
-- 注意:表名/字段名保持与后端 mapper 一致,后端代码零改动
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_blog
-- ----------------------------
DROP TABLE IF EXISTS `tb_blog`;
CREATE TABLE `tb_blog` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) NOT NULL COMMENT '高校id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id(大学生)',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `images` varchar(2048) NOT NULL COMMENT '配图,最多9张,多张以","隔开',
  `content` varchar(2048) NOT NULL COMMENT 'AI 心得正文',
  `liked` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '点赞数量',
  `comments` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 23 DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Records of tb_blog  大学生 AI 心得示例
-- ----------------------------
INSERT INTO `tb_blog` VALUES
(1, 1, 2, '清华学长:10 条提示词,让 GPT 帮你写论文综述', '', '1.让模型先列大纲再分节填充,幻觉率大幅下降。2.要求输出格式为 Markdown 表格。3.把参考文献丢给它之前先要求它自检是否存在。4.多轮追问比分一次给超长 prompt 更稳。详细的 10 条我整理在图里,欢迎拍砖。', 186, 36, '2026-09-12 19:50:01', '2026-09-20 14:26:34'),
(2, 3, 1, '浙大日常:Copilot 让我把作业效率提升了 2 倍', '', '这学期开始用 Copilot 写工程课作业,从面向搜索引擎变成面向模型对话。碰壁的几次都是需求没说清,后来固定用「目标-约束-验收标准」三段式描述,通过率肉眼可见地涨。还没试过的同学可以冲。', 220, 58, '2026-09-13 08:20:00', '2026-09-21 11:00:00'),
(3, 2, 5, '北大学姐:Stable Diffusion 生图参数调优心法', '', '采样步数不是越多越好,SD 1.5 用 20~30 步最划算;CFG 7 之后容易过度锐化人脸。调参记录用 Word 表格记下来,比盲调快十倍。', 95, 12, '2026-09-15 22:10:00', '2026-09-18 09:00:00'),
(4, 1, 1, '用 3 万 Token 把期末报告做成 40 页 PPT 的经验', '', '大纲:先让模型生成目录,再按页写讲稿,最后用「一句话提醒」拆分到每页。全文让模型总结摘要比直接丢正文省一半 Token,还能避免关键信息被截断。', 77, 9, '2026-09-18 17:30:00', '2026-09-19 10:00:00');

-- ----------------------------
-- Table structure for tb_blog_comments (后端暂无接口,预留)
-- ----------------------------
DROP TABLE IF EXISTS `tb_blog_comments`;
CREATE TABLE `tb_blog_comments` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '心得id',
  `parent_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的1级评论id,如果是一级评论,则值为0',
  `answer_id` bigint(20) UNSIGNED NOT NULL COMMENT '回复的评论id',
  `content` varchar(255) NOT NULL COMMENT '回复的内容',
  `liked` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED NULL DEFAULT NULL COMMENT '状态,0:正常,1:被举报,2:禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Table structure for tb_follow
-- ----------------------------
DROP TABLE IF EXISTS `tb_follow`;
CREATE TABLE `tb_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4;

-- 关注关系:便于演示"共同关注"
INSERT INTO `tb_follow` (`user_id`, `follow_user_id`) VALUES
(1, 5), (1, 2), (2, 1), (2, 5), (5, 1), (5, 2);

-- ----------------------------
-- Table structure for tb_seckill_voucher
-- ----------------------------
DROP TABLE IF EXISTS `tb_seckill_voucher`;
CREATE TABLE `tb_seckill_voucher` (
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的套餐id',
  `stock` int(8) NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` timestamp NULL DEFAULT NULL COMMENT '生效时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Token 秒杀包,与套餐一对一';

INSERT INTO `tb_seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
(6, 200, '2026-09-29 00:00:00', '2026-12-31 23:59:59'),
(7, 50,  '2026-09-29 00:00:00', '2026-12-31 23:59:59'),
(8, 500, '2026-09-29 00:00:00', '2026-12-31 23:59:59');

-- ----------------------------
-- Table structure for tb_shop  (高校)
-- ----------------------------
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '高校名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '高校类别id',
  `images` varchar(1024) NOT NULL COMMENT '高校形象图,多个图片以","隔开',
  `area` varchar(128) DEFAULT NULL COMMENT '所在城市/片区',
  `address` varchar(255) NOT NULL COMMENT '详细地址',
  `x` double UNSIGNED NOT NULL COMMENT '经度',
  `y` double UNSIGNED NOT NULL COMMENT '纬度',
  `avg_price` bigint(10) UNSIGNED DEFAULT NULL COMMENT '人均月消费(元),用于距离排序外展示',
  `sold` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '累计购买人数',
  `comments` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '心得/讨论数',
  `score` int(2) UNSIGNED ZEROFILL NOT NULL COMMENT '满意度评分,1~5分乘10保存',
  `open_hours` varchar(32) DEFAULT NULL COMMENT '算力池开放时段',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `foreign_key_type`(`type_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 15 DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_shop` VALUES
(1, '清华大学', 1, '', '北京海淀', '北京市海淀区双清路30号', 116.326000, 40.003000, 328, 0000004235, 0000001856, 48, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(2, '北京大学', 1, '', '北京海淀', '北京市海淀区颐和园路5号',  116.311000, 39.995000, 315, 0000002210, 0000001002, 49, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(3, '浙江大学', 1, '', '浙江杭州', '浙江省杭州市西湖区余杭塘路866号', 120.090000, 30.260000, 268, 0000005960, 0000002430, 47, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(4, '上海交通大学', 1, '', '上海闵行', '上海市闵行区东川路800号', 121.431000, 31.031000, 305, 0000002116, 0000000880, 48, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(5, '复旦大学', 1, '', '上海杨浦', '上海市杨浦区邯郸路220号', 121.744000, 31.289000, 288, 0000001908, 0000000761, 48, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(6, '武汉大学', 1, '', '湖北武汉', '湖北省武汉市武昌区珞珈山街道', 114.361000, 30.537000, 232, 0000001780, 0000000662, 46, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(7, '西安交通大学', 1, '', '陕西西安', '陕西省西安市碑林区咸宁西路28号', 108.983000, 34.244000, 218, 0000001520, 0000000540, 45, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(8, '哈尔滨工业大学', 1, '', '黑龙江哈尔滨', '黑龙江省哈尔滨市南岗区西大直街92号', 126.639000, 45.745000, 205, 0000001666, 0000000601, 47, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(9, '中国科学技术大学', 1, '', '安徽合肥', '安徽省合肥市包河区金寨路96号', 117.274000, 31.841000, 240, 0000001420, 0000000512, 49, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(10, '四川大学', 1, '', '四川成都', '四川省成都市武侯区一环路南一段24号', 104.066000, 30.633000, 198, 0000001388, 0000000480, 45, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(11, '郑州大学', 2, '', '河南郑州', '河南省郑州市高新区科学大道100号', 113.560000, 34.820000, 165, 0000001120, 0000000330, 43, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(12, '上海科技大学', 3, '', '上海浦东', '上海市浦东新区华夏中路393号', 121.594000, 31.180000, 220, 0000000980, 0000000288, 46, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(13, '杭州电子科技大学', 4, '', '浙江杭州', '浙江省杭州市下沙高教园区2号大街1158号', 120.150000, 30.172000, 150, 0000001380, 0000000401, 42, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00'),
(14, '深圳职业技术学院', 5, '', '广东深圳', '广东省深圳市南山区留仙大道2190号', 113.945000, 22.546000, 88, 0000002600, 0000000355, 41, '00:00-24:00', '2026-09-01 09:00:00', '2026-09-20 12:00:00');

-- ----------------------------
-- Table structure for tb_shop_type  (高校类别)
-- ----------------------------
DROP TABLE IF EXISTS `tb_shop_type`;
CREATE TABLE `tb_shop_type` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '类别名称',
  `icon` varchar(255) DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 11 DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_shop_type` VALUES
(1, '985名校',   '/types/985.svg',  1, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(2, '211工程',   '/types/211.svg',  2, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(3, '双一流',    '/types/sxy.svg',  3, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(4, '普通本科',  '/types/bk.svg',   4, '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
(5, '高职院校',  '/types/gz.svg',   5, '2026-09-01 09:00:00', '2026-09-01 09:00:00');

-- ----------------------------
-- Table structure for tb_sign (签到,当前走 Redis,此表预留)
-- ----------------------------
DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `year` year NOT NULL COMMENT '签到的年',
  `month` tinyint(2) NOT NULL COMMENT '签到的月',
  `date` date NOT NULL COMMENT '签到的日期',
  `is_backup` tinyint(1) UNSIGNED DEFAULT NULL COMMENT '是否补签',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Table structure for tb_user (大学生)
-- ----------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) DEFAULT '' COMMENT '密码,加密存储',
  `nick_name` varchar(32) DEFAULT '' COMMENT '昵称',
  `icon` varchar(255) DEFAULT '' COMMENT '头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uniqe_key_phone`(`phone`)
) ENGINE = InnoDB AUTO_INCREMENT = 1010 DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_user` VALUES
(1,  '13686869696', '', '清华·小林',  '', '2026-09-01 10:27:19', '2026-09-20 16:04:00'),
(2,  '13838411438', '', '浙大·阿柯',  '', '2026-09-01 15:14:39', '2026-09-20 09:30:00'),
(5,  '13456789001', '', '武大·多多',  '', '2026-09-02 16:11:33', '2026-09-21 09:09:20'),
(4,  '13456789011', '', '上交·晨晨',  '', '2026-09-02 12:07:53', '2026-09-22 11:00:00'),
(10, '13688668889', '', '复旦·小夏',  '', '2026-09-03 10:50:47', '2026-09-22 12:00:00'),
(11, '13688668890', '', '西交·启航',  '', '2026-09-03 10:50:47', '2026-09-22 12:00:00'),
(12, '13688668891', '', '哈工·极客',  '', '2026-09-03 10:50:47', '2026-09-22 12:00:00');

-- ----------------------------
-- Table structure for tb_user_info (个人资料扩展)
-- ----------------------------
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '主键,用户id',
  `city` varchar(64) DEFAULT '' COMMENT '城市',
  `introduce` varchar(128) DEFAULT NULL COMMENT '个人介绍',
  `fans` int(8) UNSIGNED DEFAULT 0 COMMENT '粉丝数量',
  `followee` int(8) UNSIGNED DEFAULT 0 COMMENT '关注的数量',
  `gender` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '性别,0:男,1:女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `credits` int(8) UNSIGNED DEFAULT 0 COMMENT '积分',
  `level` tinyint(1) UNSIGNED DEFAULT 0 COMMENT '算力会员级别,0~9级',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_user_info` VALUES
(1,  '北京', '清华计算机大二,专注 LLM 应用', 12, 2, 0, '2006-06-01', 320, 3, '2026-09-01 10:27:19', '2026-09-20 16:04:00'),
(2,  '杭州', '浙大软工大三,Copilot 重度用户', 28, 2, 1, '2005-03-14', 215, 2, '2026-09-01 15:14:39', '2026-09-20 09:30:00'),
(5,  '武汉', '武大新闻系,AI 绘画爱好者', 9, 2, 1, '2006-11-20', 156, 1, '2026-09-02 16:11:33', '2026-09-21 09:09:20');

-- ----------------------------
-- Table structure for tb_voucher (大模型 Token 套餐)
-- ----------------------------
DROP TABLE IF EXISTS `tb_voucher`;
CREATE TABLE `tb_voucher` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '高校id',
  `title` varchar(255) NOT NULL COMMENT '套餐标题',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint(10) UNSIGNED NOT NULL COMMENT '支付金额,单位是分。例如2500代表25元',
  `actual_value` bigint(10) NOT NULL COMMENT 'Token 数量,单位个。例如300000代表30万Token',
  `type` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0,普通套餐;1,秒杀包',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1,上架; 2,下架; 3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 10 DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_voucher` VALUES
(1, 1, '月卡·30万 Token',   'AI 对话 / 代码助手畅聊一个月', '限本人使用\n有效期30天\n支持 Web/API 双端\n到期自动失效', 2500,  300000,  0, 1, '2026-09-05 09:42:39', '2026-09-05 09:43:31'),
(2, 1, '季卡·100万 Token',  '季度常用,综合性价比之选',     '限本人使用\n有效期90天\n支持团队内分享(最多5人)\n到期自动失效',        6800,  1000000, 0, 1, '2026-09-05 09:50:00', '2026-09-05 09:50:00'),
(3, 1, '年卡·500万 Token',  '全年算力自由,学生党年包',     '限本人使用\n有效期365天\n可开通 API 优先通道\n到期自动失效',      19800, 5000000, 0, 1, '2026-09-05 10:00:00', '2026-09-05 10:00:00'),
(4, 1, '新用户专享·10万 Token', '注册即享的白菜价体验包',     '限新用户购买1次\n有效期30天\n不可叠加',         100,   100000,  0, 1, '2026-09-05 10:10:00', '2026-09-05 10:10:00'),
(5, 3, '浙大·校园联名·月卡 30万 Token', '浙大学生专属优惠', '仅限浙大在读学生\n凭校园网 IP 激活\n有效期30天', 2000, 300000, 0, 1, '2026-09-05 10:20:00', '2026-09-05 10:20:00'),
(6, 1, '秒杀·10元抢 10万 Token', '限量 200 份,手慢无', '秒杀价每人限购1份\n有效期30天\n不可退款', 1000, 100000, 1, 1, '2026-09-05 10:30:00', '2026-09-05 10:30:00'),
(7, 1, '秒杀·29.9元抢 50万 Token', '骨折价大容量包,限量 50 份', '秒杀价每人限购1份\n有效期60天\n不可退款', 2990, 500000, 1, 1, '2026-09-05 10:35:00', '2026-09-05 10:35:00'),
(8, 1, '秒杀·5元体验 5万 Token', '新手上车包,限量 500 份', '秒杀价每人限购1份\n有效期7天\n不可退款', 500,  50000,  1, 1, '2026-09-05 10:40:00', '2026-09-05 10:40:00');

-- ----------------------------
-- Table structure for tb_voucher_order
-- ----------------------------
DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '下单的用户id',
  `voucher_id` bigint(20) UNSIGNED NOT NULL COMMENT '购买的套餐id',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式 1:余额支付;2:支付宝;3:微信',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单状态,1:未支付;2:已支付;3:已核销;4:已取消;5:退款中;6:已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO `tb_voucher_order` VALUES
(1688888888888888888, 1, 6, 1, 2, '2026-09-20 10:00:00', '2026-09-20 10:00:05', NULL, NULL, '2026-09-20 10:00:05');

SET FOREIGN_KEY_CHECKS = 1;