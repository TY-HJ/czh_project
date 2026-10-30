package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报修工单 —— 核心实体
 * 状态: 0待派单 1处理中 2待验收 3已完成 4已关闭
 */
@Data
@TableName("repair_order")
public class RepairOrder {
    public static final int STATUS_PENDING_DISPATCH = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_PENDING_ACCEPTANCE = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_CLOSED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long typeId;
    private Long locationId;
    private String title;
    private String description;
    private String images;      // 逗号分隔的URL
    private Integer status;
    private Long workerId;
    private Long adminId;
    private String priority;    // NORMAL / URGENT
    private Integer urgeCount;
    private String handleNote;
    private String dispatchType;    // AUTO-智能派单 MANUAL-手动派单
    private java.math.BigDecimal dispatchScore; // 派单时Top1综合得分
    private Integer reassignCount;  // 自动改派次数
    private String triedWorkers;    // 已尝试维修工ID,逗号分隔
    private LocalDateTime workerAcceptTime; // 维修工接单确认时间
    private LocalDateTime submitTime;
    private LocalDateTime assignTime;
    private LocalDateTime finishTime;
    private LocalDateTime acceptTime;
}
