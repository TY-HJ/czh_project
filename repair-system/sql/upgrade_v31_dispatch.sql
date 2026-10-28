-- ============================================================
-- V3.1 派单功能升级(简化版, 可重复执行, 不破坏现有业务数据)
--
-- 自动派单规则只有 3 个条件(全部满足才自动派):
--   1) 派单规则页的"自动派单开关"已打开
--   2) 有技能匹配且在岗的维修工
--   3) 其中至少一人未满负荷
-- 不满足即转人工: 工单时间轴写明原因 + 站内通知管理员。
--
-- 本脚本职责:
--   1) 删除已废弃的 repair_type.auto_dispatch(类型级派单开关)
--   2) 删除已废弃的 dispatch_rule.min_candidates(候选下限)
--   3) 新增报修类型「网络故障」+ 专职维修工「周师傅」(仅网络故障技能)
--
-- 执行方式: mysql -uroot -p repair_system < sql/upgrade_v31_dispatch.sql
-- ============================================================

USE repair_system;

-- ---------- 1. 删除废弃列 repair_type.auto_dispatch ----------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'repair_type' AND COLUMN_NAME = 'auto_dispatch');
SET @ddl := IF(@exist > 0,
  'ALTER TABLE `repair_type` DROP COLUMN `auto_dispatch`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 删除废弃列 dispatch_rule.min_candidates ----------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dispatch_rule' AND COLUMN_NAME = 'min_candidates');
SET @ddl := IF(@exist > 0,
  'ALTER TABLE `dispatch_rule` DROP COLUMN `min_candidates`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 新增「网络故障」报修类型 ----------
INSERT INTO `repair_type` (`type_name`, `sort`)
SELECT '网络故障', 7 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `repair_type` WHERE `type_name` = '网络故障');

-- ---------- 4. 新增专职维修工「周师傅」 ----------
-- 密码同样是 123456 (与其它测试账号一致的 BCrypt 哈希)
-- 注意: 用手机号判断"是否已存在", 而不是用户名 —— 用户名可能在界面上被改过, 用用户名判断会导致重复插入
INSERT INTO `user` (`username`, `password`, `real_name`, `phone`, `role`, `status`)
SELECT 'zhoulei', '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '周师傅', '13800000007', 'WORKER', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `phone` = '13800000007');

-- 周师傅只接网络故障(技能专精): 网络类工单只有他能被自动派到
INSERT INTO `worker_skill` (`worker_id`, `type_id`)
SELECT u.id, t.id
FROM `user` u, `repair_type` t
WHERE u.phone = '13800000007' AND t.type_name = '网络故障'
  AND NOT EXISTS (SELECT 1 FROM `worker_skill` ws WHERE ws.worker_id = u.id AND ws.type_id = t.id);

-- ---------- 5. 新增维修工「钱师傅」(水电+门窗, 与李师傅/王师傅技能重叠) ----------
-- 作用: 让水电、门窗各有两位候选, 自动派单的打分与负荷均衡逻辑真正生效
INSERT INTO `user` (`username`, `password`, `real_name`, `phone`, `role`, `status`)
SELECT 'qiancheng', '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '钱师傅', '13800000008', 'WORKER', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `phone` = '13800000008');

INSERT INTO `worker_skill` (`worker_id`, `type_id`)
SELECT u.id, t.id
FROM `user` u, `repair_type` t
WHERE u.phone = '13800000008' AND t.type_name IN ('水电维修', '门窗维修')
  AND NOT EXISTS (SELECT 1 FROM `worker_skill` ws WHERE ws.worker_id = u.id AND ws.type_id = t.id);

-- ---------- 验证 ----------
SELECT t.id, t.type_name, t.sort,
       (SELECT COUNT(*) FROM worker_skill ws WHERE ws.type_id = t.id) AS skill_workers
FROM `repair_type` t ORDER BY t.sort;
SELECT id, username, real_name, role, status FROM `user` WHERE role = 'WORKER';
SELECT id, auto_dispatch_enabled, max_load, reassign_limit, reassign_hours FROM `dispatch_rule`;
