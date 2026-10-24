-- ============================================================
-- 社区报修工单管理系统 V1.0 -> V2.0 增量升级脚本
-- 适用: 已按 V1.0 init.sql 初始化的数据库
-- 全新环境请直接执行 init.sql, 无需本脚本
-- 使用方法: mysql -uroot -p < upgrade_v2.sql
-- ============================================================

USE repair_system;

-- 1. user 表: 登录锁定字段(S10)
ALTER TABLE `user`
  ADD COLUMN `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER `status`,
  ADD COLUMN `lock_until` DATETIME DEFAULT NULL COMMENT '锁定截止时间' AFTER `login_fail_count`;

-- 2. repair_order 表: 智能派单相关字段(S11)
ALTER TABLE `repair_order`
  ADD COLUMN `dispatch_type` VARCHAR(10) DEFAULT NULL COMMENT '派单方式: AUTO-智能派单 MANUAL-手动派单' AFTER `handle_note`,
  ADD COLUMN `dispatch_score` DECIMAL(5,2) DEFAULT NULL COMMENT '派单时Top1综合得分' AFTER `dispatch_type`,
  ADD COLUMN `score_detail` TEXT DEFAULT NULL COMMENT '派单时TopN候选人得分明细JSON' AFTER `dispatch_score`,
  ADD COLUMN `reassign_count` INT NOT NULL DEFAULT 0 COMMENT '自动改派次数' AFTER `score_detail`,
  ADD COLUMN `tried_workers` VARCHAR(500) DEFAULT NULL COMMENT '已尝试维修工ID,逗号分隔' AFTER `reassign_count`,
  ADD COLUMN `worker_accept_time` DATETIME DEFAULT NULL COMMENT '维修工接单确认时间' AFTER `tried_workers`;

-- 3. 维修工技能标签表(新增, 多对多)
CREATE TABLE `worker_skill` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `worker_id`  BIGINT NOT NULL COMMENT '维修工ID',
  `type_id`    BIGINT NOT NULL COMMENT '报修类型ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_type` (`worker_id`, `type_id`)
) ENGINE=InnoDB COMMENT='维修工技能标签';

-- 4. 智能派单规则表(新增, 单行配置)
CREATE TABLE `dispatch_rule` (
  `id`                    BIGINT NOT NULL AUTO_INCREMENT,
  `auto_dispatch_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '自动派单总开关: 1-开 0-关',
  `w_load`                DECIMAL(4,2) NOT NULL DEFAULT 0.40 COMMENT '负荷分权重',
  `w_rating`              DECIMAL(4,2) NOT NULL DEFAULT 0.35 COMMENT '历史评分权重',
  `w_familiarity`         DECIMAL(4,2) NOT NULL DEFAULT 0.25 COMMENT '楼栋熟悉度权重',
  `urgency_boost`         DECIMAL(4,2) NOT NULL DEFAULT 0.50 COMMENT '紧急工单负荷权重放大系数',
  `max_load`              INT NOT NULL DEFAULT 5 COMMENT '负荷归一化上限(未完成工单数)',
  `familiarity_full`      INT NOT NULL DEFAULT 5 COMMENT '熟悉度满分所需历史完成单数',
  `default_rating`        DECIMAL(4,2) NOT NULL DEFAULT 0.60 COMMENT '无评价时的默认评分得分',
  `reassign_limit`        INT NOT NULL DEFAULT 2 COMMENT '自动改派次数上限',
  `reassign_hours`        INT NOT NULL DEFAULT 48 COMMENT '处理中超时改派阈值(小时)',
  `top_n`                 INT NOT NULL DEFAULT 5 COMMENT '得分明细存档条数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='智能派单规则(单行)';

-- 5. 操作日志表(新增, AOP审计)
CREATE TABLE `operation_log` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT DEFAULT NULL COMMENT '操作人ID(未登录为空)',
  `username`    VARCHAR(50) DEFAULT NULL COMMENT '操作人账号',
  `module`      VARCHAR(50)  NOT NULL COMMENT '业务模块',
  `action`      VARCHAR(100) NOT NULL COMMENT '动作描述',
  `method`      VARCHAR(10)  NOT NULL COMMENT '请求方式 GET/POST/PUT/DELETE',
  `uri`         VARCHAR(200) NOT NULL COMMENT '请求URI',
  `params`      VARCHAR(1000) DEFAULT NULL COMMENT '参数摘要(脱敏/截断)',
  `ip`          VARCHAR(50)  DEFAULT NULL COMMENT '来源IP',
  `success`     TINYINT NOT NULL DEFAULT 1 COMMENT '1-成功 0-失败',
  `error_msg`   VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB COMMENT='操作日志(AOP审计)';

-- 6. 知识库FAQ表(新增)
CREATE TABLE `faq` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `category`    VARCHAR(50)  NOT NULL COMMENT '分类(与报修类型名称对应)',
  `title`       VARCHAR(100) NOT NULL COMMENT '标题',
  `content`     TEXT NOT NULL COMMENT '处理步骤内容',
  `sort`        INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `view_count`  INT NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `admin_id`    BIGINT NOT NULL COMMENT '发布管理员ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB COMMENT='知识库FAQ';

-- ============================================================
-- 初始数据
-- ============================================================

-- 派单规则默认配置(单行)
INSERT INTO `dispatch_rule`
  (`auto_dispatch_enabled`,`w_load`,`w_rating`,`w_familiarity`,`urgency_boost`,`max_load`,`familiarity_full`,`default_rating`,`reassign_limit`,`reassign_hours`,`top_n`)
VALUES
  (1, 0.40, 0.35, 0.25, 0.50, 5, 5, 0.60, 2, 48, 5);

-- 维修工技能标签(lisi=ID3: 水电/门禁/公共照明; wangwu=ID4: 门窗/电梯/其他)
-- 注意: 若 V1.0 库中用户ID与此不符, 请按实际ID调整
INSERT INTO `worker_skill` (`worker_id`, `type_id`) VALUES
(3, 1), (3, 4), (3, 5),
(4, 2), (4, 3), (4, 6);

-- FAQ 示例数据(分类与报修类型名称对应)
INSERT INTO `faq` (`category`, `title`, `content`, `sort`, `admin_id`) VALUES
('水电维修', '水龙头滴水怎么处理', '1. 关闭水龙头下方三角阀切断水源;\n2. 用扳手拆下阀芯,检查陶瓷片是否磨损;\n3. 更换同型号阀芯后复位;\n4. 若仍漏水请提交报修并附照片。', 1, 1),
('水电维修', '家里突然停电自查步骤', '1. 检查空气开关是否跳闸,先合闸试一次;\n2. 拔掉大功率电器后再合闸;\n3. 查看户内是否只有自家停电(问邻居);\n4. 全楼停电请拨打物业值班电话,个别户停电请报修。', 2, 1),
('门窗维修', '窗户关不严/漏风处理', '1. 检查合页螺丝是否松动,用螺丝刀拧紧;\n2. 清理窗框密封槽内的灰尘异物;\n3. 变形严重的塑钢窗不建议自行掰正,请报修更换。', 3, 1),
('电梯故障', '电梯困人应急须知', '1. 保持冷静,持续按警铃按钮呼叫监控室;\n2. 切勿扒门或攀爬逃生;\n3. 监控室会通知维保单位30分钟内到场救援;\n4. 电梯轻微异响/楼层显示异常也请及时报修。', 4, 1),
('门禁系统', '门禁卡失灵自查', '1. 换一张卡测试,判断是卡问题还是读卡器问题;\n2. 卡失灵请联系物业前台重新写卡;\n3. 读卡器无反应/灯不亮请报修并在描述中注明单元号。', 5, 1),
('公共照明', '楼道灯不亮报修指引', '1. 确认是常亮灯还是声控灯(声控灯夜间拍手测试);\n2. 记录楼栋/单元/楼层便于师傅定位;\n3. 提交报修时选择"公共照明"并上传灯位照片。', 6, 1);
