-- ============================================================
-- repair-system 演示示例数据恢复脚本 (restore_seed.sql)
-- 作用: 恢复 init.sql 中自带的 4 条示例工单及其流转日志、评价记录
--       (与 init.sql 的种子数据完全一致, 仅在已被清空的库上使用)
-- 前提: user/repair_type/location/dispatch_rule/worker_skill 等基础数据完好
-- 执行方式: mysql -uroot -p repair_system < sql/restore_seed.sql
-- ============================================================

USE repair_system;

START TRANSACTION;

-- 示例工单(显式指定 id, 保证与下方日志/评价的 order_id 对应)
INSERT INTO `repair_order`
  (`id`,`order_no`,`user_id`,`type_id`,`location_id`,`title`,`description`,`status`,`worker_id`,`admin_id`,`priority`,`submit_time`,`assign_time`,`finish_time`,`accept_time`,`handle_note`) VALUES
  (1, '202601010001', 2, 1, 1, '水龙头漏水',       '卫生间的水龙头关不紧,一直滴水', 3, 3, 1, 'NORMAL', '2026-01-05 09:30:00', '2026-01-05 10:00:00', '2026-01-05 14:00:00', '2026-01-05 18:00:00', '更换了阀芯,已测试不漏水'),
  (2, '202601020002', 2, 3, 5, '电梯按钮失灵',     '2号楼电梯3层按钮按了没反应',   3, 4, 1, 'URGENT', '2026-01-08 20:10:00', '2026-01-09 08:30:00', '2026-01-09 10:30:00', '2026-01-09 12:00:00', '更换按钮面板,已恢复正常'),
  (3, '202602030003', 2, 2, 3, '窗户合页松动',     '阳台窗户关不严,合页螺丝松了',  2, 4, 1, 'NORMAL', '2026-02-12 15:00:00', '2026-02-13 09:00:00', NULL, NULL, NULL),
  (4, '202603040004', 2, 4, 2, '单元门禁刷卡失灵', '1单元门禁刷卡没反应,进不去门', 0, NULL, NULL, 'NORMAL', '2026-03-01 11:00:00', NULL, NULL, NULL, NULL);

-- 后续新工单从 id=5 开始自增
ALTER TABLE `repair_order` AUTO_INCREMENT = 5;

-- 流转日志
INSERT INTO `order_log` (`order_id`,`operator_id`,`action`,`remark`,`create_time`) VALUES
  (1, 2, 'SUBMIT',  '提交报修',                 '2026-01-05 09:30:00'),
  (1, 1, 'ASSIGN',  '派单给 李师傅',             '2026-01-05 10:00:00'),
  (1, 3, 'FINISH',  '更换了阀芯,已测试不漏水',     '2026-01-05 14:00:00'),
  (1, 2, 'PASS',    '验收通过,评分5星',           '2026-01-05 18:00:00'),
  (2, 2, 'SUBMIT',  '提交报修',                 '2026-01-08 20:10:00'),
  (2, 1, 'ASSIGN',  '派单给 王师傅',             '2026-01-09 08:30:00'),
  (2, 4, 'FINISH',  '更换按钮面板,已恢复正常',     '2026-01-09 10:30:00'),
  (2, 2, 'PASS',    '验收通过,评分4星',           '2026-01-09 12:00:00'),
  (3, 2, 'SUBMIT',  '提交报修',                 '2026-02-12 15:00:00'),
  (3, 1, 'ASSIGN',  '派单给 王师傅',             '2026-02-13 09:00:00'),
  (4, 2, 'SUBMIT',  '提交报修',                 '2026-03-01 11:00:00');

-- 验收评价
INSERT INTO `evaluation` (`order_id`,`worker_id`,`user_id`,`score`,`content`,`create_time`) VALUES
  (1, 3, 2, 5, '修得很快,态度好!',   '2026-01-05 18:00:00'),
  (2, 4, 2, 4, '当天就来修好了,不错', '2026-01-09 12:00:00');

COMMIT;

-- 验证
SELECT COUNT(*) AS orders_after_restore FROM repair_order;
