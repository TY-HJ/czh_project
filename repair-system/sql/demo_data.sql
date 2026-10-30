-- ============================================================
-- repair-system 演示数据脚本 (demo_data.sql) —— 精简版，共 10 条
-- 特点: 数据量小、翻页干净，但刻意保住图表和演示需要的分布：
--       · 7 种报修类型全覆盖（水电 3、网络 2，其余各 1）
--       · 三个住户都有单（张三 4 / 赵六 3 / 孙小小 3）
--       · 四个师傅都有单，且都有评分（王 5.0 / 周 5.0 / 李 4.0 / 钱 4.0）
--       · 五种状态全覆盖：待派单 1 / 处理中 2 / 待验收 2 / 已完成 4 / 已关闭 1
--       · 处理中、待验收的时间都贴近当天，不会一启动就被定时任务改派或催办
-- 可重复执行: 每次先清空工单及相关日志/评价/消息，再重建这 10 条
-- 执行: mysql -uroot -p repair_system < sql/demo_data.sql
-- ============================================================

USE repair_system;

START TRANSACTION;

DELETE FROM `order_log`;
DELETE FROM `evaluation`;
DELETE FROM `repair_order`;
ALTER TABLE `repair_order` AUTO_INCREMENT = 11;
DELETE FROM `message`;

-- 「其他」属于兜底类故障，不该只有王师傅一人能接，给李、钱各补一条（幂等）
INSERT IGNORE INTO `worker_skill` (`worker_id`,`type_id`) VALUES (3, 6), (8, 6);

INSERT INTO `repair_order`
  (`id`,`order_no`,`user_id`,`type_id`,`location_id`,`title`,`description`,`status`,`worker_id`,`admin_id`,
   `priority`,`dispatch_type`,`dispatch_score`,`submit_time`,`assign_time`,`worker_accept_time`,`finish_time`,`accept_time`,`handle_note`) VALUES
  -- 1 水电 / 张三 / 李师傅 / 已完成并评价（6 月，智能派单）
  (1, '202606110001', 2, 1, 1, '卫生间水龙头漏水',
   '卫生间洗手盆的水龙头关不紧，一直在滴水，地面已经有积水，麻烦尽快上门处理。',
   3, 3, 1, 'NORMAL', 'AUTO', 0.86,
   '2026-06-11 08:30:00', '2026-06-11 08:30:05', '2026-06-11 09:00:00', '2026-06-11 14:00:00', '2026-06-12 10:00:00',
   '更换阀芯并紧固接口，已通水测试无渗漏，已清理现场。'),

  -- 2 电梯 / 张三 / 王师傅 / 已完成并评价（9 月，紧急件，管理员派单）
  (2, '202609030001', 2, 3, 5, '电梯按钮失灵',
   '2号楼电梯3层按钮按了没反应，轿厢里的关门键也不灵，麻烦安排维保。',
   3, 4, 1, 'URGENT', 'MANUAL', NULL,
   '2026-09-03 20:10:00', '2026-09-03 20:15:00', '2026-09-04 08:30:00', '2026-09-04 10:30:00', '2026-09-04 12:00:00',
   '更换按钮面板并清洁触点，已载人试运行正常，已恢复使用。'),

  -- 3 门窗 / 赵六 / 王师傅 / 待验收（10-28 提交，10-29 完工）
  (3, '202610280001', 5, 2, 3, '卧室窗户关不严',
   '卧室窗户关到底还有一条缝，晚上漏风也有噪音，锁扣好像对不上。',
   2, 4, 1, 'NORMAL', 'MANUAL', NULL,
   '2026-10-28 09:00:00', '2026-10-28 10:30:00', '2026-10-28 11:00:00', '2026-10-29 08:00:00', NULL,
   '调整五金件并更换密封条，已开关测试顺畅，已清理现场。'),

  -- 4 门禁 / 孙小小 / 未派单（10-26 提交，必然触发"超24h未派"标红）
  (4, '202610260001', 6, 4, 2, '单元门禁刷卡失灵',
   '1单元门禁刷卡没有反应，指示灯也不亮，只能用钥匙或者等别人开门。',
   0, NULL, NULL, 'NORMAL', NULL, NULL,
   '2026-10-26 10:00:00', NULL, NULL, NULL, NULL, NULL),

  -- 5 水电 / 赵六 / 钱师傅 / 处理中（10-28 派单，未超 48h）
  (5, '202610280002', 5, 1, 4, '热水器不出热水',
   '热水器打不着火，洗澡没有热水，已经试过重启还是不行。',
   1, 8, 1, 'NORMAL', 'AUTO', 0.78,
   '2026-10-28 14:00:00', '2026-10-28 14:00:05', '2026-10-28 14:30:00', NULL, NULL, NULL),

  -- 6 网络 / 张三 / 周师傅 / 处理中（10-28 派单，未超 48h）
  (6, '202610280003', 2, 7, 6, '家里宽带连不上',
   '家里的宽带从昨晚开始连不上，路由器指示灯是红的，重启过两次还是不行。',
   1, 7, 1, 'NORMAL', 'AUTO', 0.91,
   '2026-10-28 19:00:00', '2026-10-28 19:00:05', '2026-10-28 19:20:00', NULL, NULL, NULL),

  -- 7 照明 / 孙小小 / 李师傅 / 待验收（10-27 提交，10-28 完工）
  (7, '202610270001', 6, 5, 1, '楼道灯不亮',
   '3号楼2单元4层楼道灯不亮，晚上上下楼看不清，有老人反映容易踩空。',
   2, 3, 1, 'NORMAL', 'MANUAL', NULL,
   '2026-10-27 15:00:00', '2026-10-27 16:30:00', '2026-10-27 17:00:00', '2026-10-28 10:00:00', NULL,
   '更换损坏灯管并紧固接线，已通电测试正常，已清理现场。'),

  -- 8 其他 / 赵六 / 王师傅 / 已关闭（9 月，管理员关闭）
  (8, '202609150001', 5, 6, 3, '楼道堆放杂物',
   '楼道拐角堆了不少纸箱和旧家具，走路要侧身，也担心消防问题。',
   4, 4, 1, 'NORMAL', 'MANUAL', NULL,
   '2026-09-15 11:00:00', '2026-09-15 11:20:00', '2026-09-15 13:00:00', NULL, NULL, NULL),

  -- 9 网络 / 孙小小 / 周师傅 / 已完成并评价（7 月，智能派单）
  (9, '202607200001', 6, 7, 5, '房间网口没网',
   '书房墙上的网口插网线没有网络，客厅的网口是正常的，不确定是面板还是线路问题。',
   3, 7, 1, 'NORMAL', 'AUTO', 0.83,
   '2026-07-20 10:00:00', '2026-07-20 10:00:05', '2026-07-20 10:30:00', '2026-07-20 16:00:00', '2026-07-21 09:00:00',
   '重做水晶头并更换面板，已测速确认正常，已告知住户使用方法。'),

  -- 10 水电 / 张三 / 钱师傅 / 已完成并评价（8 月，智能派单）
  (10, '202608120001', 2, 1, 6, '厨房下水管堵塞',
   '厨房洗菜池下水越来越慢，现在基本排不下去，用疏通剂试过没有效果。',
   3, 8, 1, 'NORMAL', 'AUTO', 0.80,
   '2026-08-12 09:20:00', '2026-08-12 09:20:05', '2026-08-12 10:10:00', '2026-08-12 15:30:00', '2026-08-13 09:00:00',
   '疏通下水管道并清理滤网，已通水测试排水顺畅，已清理现场。');

-- 1 号单补"管理员改派"场景：原先自动派给钱师傅，半小时后管理员改派给李师傅
UPDATE `repair_order`
SET `assign_time` = '2026-06-11 09:30:00', `worker_accept_time` = '2026-06-11 10:00:00',
    `reassign_count` = 1, `tried_workers` = '8,3'
WHERE `id` = 1;

-- 3 号单补"催办"场景：处理中被住户催办 2 次
UPDATE `repair_order` SET `urge_count` = 2 WHERE `id` = 3;

-- 验收评价：四个师傅各一条，评分拉开档次
-- 必须放在生成 PASS 日志之前，否则 JOIN evaluation 查不到数据，验收日志会缺失
INSERT INTO `evaluation` (`order_id`,`worker_id`,`user_id`,`score`,`content`,`create_time`) VALUES
  (1,  3, 2, 4, '师傅来得很快，问题一次就解决了', '2026-06-12 10:00:00'),
  (2,  4, 2, 5, '紧急件当天就处理好，很专业',       '2026-09-04 12:00:00'),
  (9,  7, 6, 5, '网速恢复正常，还帮忙整理了线路',   '2026-07-21 09:00:00'),
  (10, 8, 2, 4, '疏通得很干净，态度不错',           '2026-08-13 09:00:00');

-- 流转日志：按工单时间字段批量生成，保证时间轴与工单完全一致
-- 1 号单的日志手写（含"先派给钱师傅、后改派给李师傅"两个节点），故排除在外
INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT `id`, `user_id`, 'SUBMIT', '提交报修', `submit_time` FROM `repair_order` WHERE `id` <> 1;

INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT o.`id`,
       IF(o.`dispatch_type` = 'AUTO', 0, 1),
       IF(o.`dispatch_type` = 'AUTO', 'AUTO_ASSIGN', 'ASSIGN'),
       IF(o.`dispatch_type` = 'AUTO',
          CONCAT('智能派单给 ', u.`real_name`, ' (综合得分', o.`dispatch_score`, ')'),
          CONCAT('派单给 ', u.`real_name`)),
       o.`assign_time`
FROM `repair_order` o JOIN `user` u ON u.`id` = o.`worker_id`
WHERE o.`worker_id` IS NOT NULL AND o.`assign_time` IS NOT NULL AND o.`id` <> 1;

INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT `id`, `worker_id`, 'ACCEPT', '维修工已接单,维修中', `worker_accept_time`
FROM `repair_order` WHERE `worker_accept_time` IS NOT NULL AND `id` <> 1;

INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT `id`, `worker_id`, 'FINISH', `handle_note`, `finish_time`
FROM `repair_order` WHERE `finish_time` IS NOT NULL AND `id` <> 1;

INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT o.`id`, o.`user_id`, 'PASS', CONCAT('验收通过,评分', e.`score`, '星'), o.`accept_time`
FROM `repair_order` o JOIN `evaluation` e ON e.`order_id` = o.`id`
WHERE o.`id` <> 1;

INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`)
SELECT `id`, 1, 'CLOSE', '管理员关闭工单', '2026-09-16 09:00:00'
FROM `repair_order` WHERE `status` = 4;

-- 1 号单完整时间轴：自动派给钱师傅 → 管理员改派给李师傅 → 接单 → 完工 → 验收
INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`) VALUES
  (1, 2, 'SUBMIT',      '提交报修', '2026-06-11 08:30:00'),
  (1, 0, 'AUTO_ASSIGN', '智能派单给 钱师傅 (综合得分0.86)', '2026-06-11 08:30:05'),
  (1, 1, 'REASSIGN',    '管理员改派: 钱师傅 → 李师傅', '2026-06-11 09:30:00'),
  (1, 3, 'ACCEPT',      '维修工已接单,维修中', '2026-06-11 10:00:00'),
  (1, 3, 'FINISH',      '更换阀芯并紧固接口，已通水测试无渗漏，已清理现场。', '2026-06-11 14:00:00'),
  (1, 2, 'PASS',        '验收通过,评分4星', '2026-06-12 10:00:00');

-- 3 号单：处理中被催办两次（住户赵六）
INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`) VALUES
  (3, 5, 'URGE', '第1次催办', '2026-10-28 18:00:00'),
  (3, 5, 'URGE', '第2次催办', '2026-10-28 22:00:00');

COMMIT;

-- 验证
SELECT '工单总数' item, COUNT(*) val FROM `repair_order`
UNION ALL SELECT '日志总数', COUNT(*) FROM `order_log`
UNION ALL SELECT '评价总数', COUNT(*) FROM `evaluation`
UNION ALL SELECT '消息总数', COUNT(*) FROM `message`;

SELECT `status`, COUNT(*) n FROM `repair_order` GROUP BY `status` ORDER BY `status`;
