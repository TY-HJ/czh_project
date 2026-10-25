-- ============================================================
-- V3.1 派单规则专项升级 (可重复执行, 不破坏现有业务数据)
-- 1) repair_type 增加"派单方式"开关: 类型可单独设为人工派单
-- 2) dispatch_rule 增加"可派候选人数下限" min_candidates
-- 3) 新增报修类型「网络故障」+ 专职维修工「周师傅」(仅网络故障技能)
-- 4) 电梯故障默认为人工派单(特种设备, 需管理员联系维保单位)
-- 执行方式: mysql -uroot -p repair_system < sql/upgrade_v31_dispatch.sql
-- ============================================================

USE repair_system;

-- ---------- 1. repair_type.auto_dispatch ----------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'repair_type' AND COLUMN_NAME = 'auto_dispatch');
SET @ddl := IF(@exist = 0,
  'ALTER TABLE `repair_type` ADD COLUMN `auto_dispatch` TINYINT NOT NULL DEFAULT 1 COMMENT ''派单方式: 1-系统自动派单 0-必须管理员人工派单'' AFTER `sort`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. dispatch_rule.min_candidates ----------
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dispatch_rule' AND COLUMN_NAME = 'min_candidates');
SET @ddl := IF(@exist = 0,
  'ALTER TABLE `dispatch_rule` ADD COLUMN `min_candidates` INT NOT NULL DEFAULT 1 COMMENT ''可派候选人数下限, 少于该值转人工'' AFTER `max_load`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 新增「网络故障」报修类型 ----------
INSERT INTO `repair_type` (`type_name`, `sort`, `auto_dispatch`)
SELECT '网络故障', 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `repair_type` WHERE `type_name` = '网络故障');

-- ---------- 4. 新增专职维修工「周师傅」 ----------
-- 密码同样是 123456 (与其它测试账号一致的 BCrypt 哈希)
INSERT INTO `user` (`username`, `password`, `real_name`, `phone`, `role`, `status`)
SELECT 'zhoubo', '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '周师傅', '13800000007', 'WORKER', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'zhoubo');

-- 周师傅只接网络故障(技能专精): 网络类工单只有他能被自动派到
INSERT INTO `worker_skill` (`worker_id`, `type_id`)
SELECT u.id, t.id
FROM `user` u, `repair_type` t
WHERE u.username = 'zhoubo' AND t.type_name = '网络故障'
  AND NOT EXISTS (SELECT 1 FROM `worker_skill` ws WHERE ws.worker_id = u.id AND ws.type_id = t.id);

-- ---------- 5. 电梯故障改为人工派单 ----------
UPDATE `repair_type` SET `auto_dispatch` = 0 WHERE `type_name` = '电梯故障';

-- 其余类型保持自动派单(显式声明, 便于重复执行时纠正误改)
UPDATE `repair_type` SET `auto_dispatch` = 1
WHERE `type_name` IN ('水电维修', '门窗维修', '门禁系统', '公共照明', '其他', '网络故障');

-- ---------- 6. 规则默认值兜底 ----------
UPDATE `dispatch_rule` SET `min_candidates` = 1 WHERE `min_candidates` IS NULL OR `min_candidates` < 1;

-- ---------- 验证 ----------
SELECT t.id, t.type_name, t.auto_dispatch,
       (SELECT COUNT(*) FROM worker_skill ws WHERE ws.type_id = t.id) AS skill_workers
FROM `repair_type` t ORDER BY t.sort;
SELECT id, username, real_name, role, status FROM `user` WHERE role = 'WORKER';
SELECT id, auto_dispatch_enabled, min_candidates, max_load, reassign_limit, reassign_hours FROM `dispatch_rule`;
