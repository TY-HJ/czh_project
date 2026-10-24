-- ============================================================
-- repair-system 演示数据清理脚本 (cleanup_demo.sql)
-- 目的: 清理测试残留与老种子工单, 让系统数据干净、统计真实
-- 影响范围: repair_system 库
--   1) 删除 3 个 E2E 测试残留账号 (e2eu92498860 / e2eu92498998 / e2eu92499025, 均无工单)
--   2) 删除 4 条 2026 年 1~3 月的老种子工单 (id 1~4) 及其流转日志与评价记录
--      - 202601010001 已完成 / 202601020002 已完成
--      - 202602030003 待验收 / 202603040004 处理中(压在维修工任务列表最前的老单)
-- 注意: 站内消息(message)只保存文案不含工单外键, 历史消息文案仍会提到这些工单号, 不影响功能
-- 执行方式: mysql -uroot -p repair_system < sql/cleanup_demo.sql
-- ============================================================

USE repair_system;

START TRANSACTION;

-- 1. 老种子工单的流转日志
DELETE FROM order_log WHERE order_id BETWEEN 1 AND 4;

-- 2. 老种子工单的验收评价
DELETE FROM evaluation WHERE order_id BETWEEN 1 AND 4;

-- 3. 老种子工单本身
DELETE FROM repair_order WHERE id BETWEEN 1 AND 4;

-- 4. E2E 测试残留账号(已确认名下无任何工单)
DELETE FROM user WHERE username IN ('e2eu92498860', 'e2eu92498998', 'e2eu92499025');

COMMIT;

-- 验证
SELECT '剩余工单数' AS item, COUNT(*) AS val FROM repair_order
UNION ALL
SELECT '剩余用户数', COUNT(*) FROM user;
