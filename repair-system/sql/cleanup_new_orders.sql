-- ============================================================
-- repair-system 新增工单清理脚本 (cleanup_new_orders.sql)
-- 作用: 删除"你自己测试提交"的工单(即 10 条演示工单之外的, id > 10)
--       及其流转日志、验收评价、提及这些工单号的站内消息
-- 不动: 1~10 号演示工单、账号、字典、公告、FAQ、操作日志
-- 特点: 可重复执行, 清完把自增重置为 11, 下次新单仍从 11 号开始
-- 说明: 想彻底恢复到标准演示态, 直接跑 demo_data.sql 更省事
-- 执行方式: mysql -uroot -p repair_system < sql/cleanup_new_orders.sql
-- ============================================================

USE repair_system;

START TRANSACTION;

-- 1. 先删掉提及这些工单号的站内消息(message 无工单外键, 只能按文案里的单号匹配)
DELETE m FROM `message` m
JOIN `repair_order` o ON m.content LIKE CONCAT('%', o.order_no, '%')
WHERE o.id > 10;

-- 2. 新工单的流转日志
DELETE FROM `order_log` WHERE order_id > 10;

-- 3. 新工单的验收评价
DELETE FROM `evaluation` WHERE order_id > 10;

-- 4. 新工单本身
DELETE FROM `repair_order` WHERE id > 10;

-- 5. 自增归位: 下次提交的新单仍从 id=11 开始
ALTER TABLE `repair_order` AUTO_INCREMENT = 11;

COMMIT;

-- 验证
SELECT id, order_no, status, worker_id FROM `repair_order` ORDER BY id;
SELECT '工单总数' AS item, COUNT(*) AS val FROM `repair_order`
UNION ALL SELECT '日志总数', COUNT(*) FROM `order_log`
UNION ALL SELECT '评价总数', COUNT(*) FROM `evaluation`
UNION ALL SELECT '消息总数', COUNT(*) FROM `message`;
