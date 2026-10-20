-- ============================================================
-- 社区报修工单管理系统 数据库初始化脚本 (V2.0 社区场景)
-- MySQL 8.0+  字符集 utf8mb4
-- 使用方法: mysql -uroot -p < init.sql
-- 默认测试账号密码均为: 123456
--   admin    / 123456   管理员
--   zhangsan / 123456   住户
--   lisi     / 123456   维修工
--   wangwu   / 123456   维修工
-- ============================================================

DROP DATABASE IF EXISTS repair_system;
CREATE DATABASE repair_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE repair_system;

-- 1. 用户表
CREATE TABLE `user` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `real_name`   VARCHAR(50)  NOT NULL COMMENT '真实姓名',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `role`        VARCHAR(20)  NOT NULL COMMENT '角色: RESIDENT-住户 WORKER-维修工 ADMIN-管理员',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常 0-禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户表';

-- 2. 报修类型字典
CREATE TABLE `repair_type` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `type_name`  VARCHAR(50) NOT NULL COMMENT '类型名称',
  `sort`       INT NOT NULL DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='报修类型字典';

-- 3. 位置字典(楼栋-单元-房间)
CREATE TABLE `location` (
  `id`       BIGINT NOT NULL AUTO_INCREMENT,
  `building` VARCHAR(50) NOT NULL COMMENT '楼栋',
  `unit`     VARCHAR(20) NOT NULL COMMENT '单元',
  `room`     VARCHAR(50) NOT NULL COMMENT '房间号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='位置字典';

-- 4. 报修工单表(核心)
CREATE TABLE `repair_order` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(30)  NOT NULL COMMENT '工单号 yyyyMMdd+4位序号',
  `user_id`     BIGINT NOT NULL COMMENT '报修人ID',
  `type_id`     BIGINT NOT NULL COMMENT '报修类型ID',
  `location_id` BIGINT NOT NULL COMMENT '位置ID',
  `title`       VARCHAR(100) NOT NULL COMMENT '标题',
  `description` TEXT COMMENT '详细描述',
  `images`      VARCHAR(1000) DEFAULT NULL COMMENT '图片URL,逗号分隔',
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0-待派单 1-处理中 2-待验收 3-已完成 4-已关闭',
  `worker_id`   BIGINT DEFAULT NULL COMMENT '维修工ID',
  `admin_id`    BIGINT DEFAULT NULL COMMENT '派单管理员ID',
  `priority`    VARCHAR(10) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL-普通 URGENT-紧急',
  `urge_count`  INT NOT NULL DEFAULT 0 COMMENT '催办次数',
  `handle_note` TEXT COMMENT '维修工处理说明',
  `submit_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `assign_time` DATETIME DEFAULT NULL COMMENT '派单时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '维修完成时间',
  `accept_time` DATETIME DEFAULT NULL COMMENT '验收时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_worker_id` (`worker_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='报修工单表';

-- 5. 工单流转记录(进度时间线)
CREATE TABLE `order_log` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT NOT NULL COMMENT '工单ID',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `action`      VARCHAR(50) NOT NULL COMMENT '动作: SUBMIT/ASSIGN/FINISH/PASS/REJECT/CANCEL/CLOSE/URGE',
  `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB COMMENT='工单流转记录';

-- 6. 验收评价表
CREATE TABLE `evaluation` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT NOT NULL COMMENT '工单ID',
  `worker_id`   BIGINT NOT NULL COMMENT '被评维修工ID',
  `user_id`     BIGINT NOT NULL COMMENT '评价人ID',
  `score`       TINYINT NOT NULL COMMENT '评分1~5',
  `content`     VARCHAR(500) DEFAULT NULL COMMENT '评语',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_worker_id` (`worker_id`)
) ENGINE=InnoDB COMMENT='验收评价表';

-- 7. 公告表
CREATE TABLE `notice` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(100) NOT NULL,
  `content`     TEXT NOT NULL,
  `admin_id`    BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='公告表';

-- 8. 站内消息表
CREATE TABLE `message` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `receiver_id` BIGINT NOT NULL COMMENT '接收人ID',
  `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
  `is_read`     TINYINT NOT NULL DEFAULT 0 COMMENT '0-未读 1-已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_receiver` (`receiver_id`, `is_read`)
) ENGINE=InnoDB COMMENT='站内消息表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 测试账号(密码均为 123456, BCrypt加密)
INSERT INTO `user` (`username`, `password`, `real_name`, `phone`, `role`) VALUES
('admin',    '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '系统管理员', '13800000001', 'ADMIN'),
('zhangsan', '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '张三',       '13800000002', 'RESIDENT'),
('lisi',     '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '李师傅',     '13800000003', 'WORKER'),
('wangwu',   '$2a$10$TvqO1iyXKBwv4E3vnAwd3OaWbqsNVMqjLxHuwEMPCB1exhj/Sw.Ma', '王师傅',     '13800000004', 'WORKER');

-- 报修类型(六类社区常见故障)
INSERT INTO `repair_type` (`type_name`, `sort`) VALUES
('水电维修', 1), ('门窗维修', 2), ('电梯故障', 3), ('门禁系统', 4), ('公共照明', 5), ('其他', 6);

-- 位置(楼栋-单元-房间)
INSERT INTO `location` (`building`, `unit`, `room`) VALUES
('1号楼', '1单元', '101'), ('1号楼', '1单元', '102'),
('1号楼', '2单元', '201'), ('1号楼', '2单元', '202'),
('2号楼', '1单元', '101'), ('3号楼', '2单元', '302');

-- 示例工单(便于统计图表有数据可看)
INSERT INTO `repair_order` (`order_no`,`user_id`,`type_id`,`location_id`,`title`,`description`,`status`,`worker_id`,`admin_id`,`priority`,`submit_time`,`assign_time`,`finish_time`,`accept_time`,`handle_note`) VALUES
('202601010001', 2, 1, 1, '水龙头漏水',       '卫生间的水龙头关不紧,一直滴水', 3, 3, 1, 'NORMAL', '2026-01-05 09:30:00', '2026-01-05 10:00:00', '2026-01-05 14:00:00', '2026-01-05 18:00:00', '更换了阀芯,已测试不漏水'),
('202601020002', 2, 3, 5, '电梯按钮失灵',     '2号楼电梯3层按钮按了没反应',   3, 4, 1, 'URGENT', '2026-01-08 20:10:00', '2026-01-09 08:30:00', '2026-01-09 10:30:00', '2026-01-09 12:00:00', '更换按钮面板,已恢复正常'),
('202602030003', 2, 2, 3, '窗户合页松动',     '阳台窗户关不严,合页螺丝松了',  2, 4, 1, 'NORMAL', '2026-02-12 15:00:00', '2026-02-13 09:00:00', NULL, NULL, NULL),
('202603040004', 2, 4, 2, '单元门禁刷卡失灵', '1单元门禁刷卡没反应,进不去门', 0, NULL, NULL, 'NORMAL', '2026-03-01 11:00:00', NULL, NULL, NULL, NULL);

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

INSERT INTO `evaluation` (`order_id`,`worker_id`,`user_id`,`score`,`content`,`create_time`) VALUES
(1, 3, 2, 5, '修得很快,态度好!',   '2026-01-05 18:00:00'),
(2, 4, 2, 4, '当天就来修好了,不错', '2026-01-09 12:00:00');

INSERT INTO `notice` (`title`,`content`,`admin_id`) VALUES
('系统使用说明', '报修请选择准确的类型与楼栋单元房间号,并尽量附上照片,便于师傅提前准备工具。维修完成后请及时验收评价。', 1);
