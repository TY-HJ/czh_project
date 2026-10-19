package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录(支撑进度时间线)
 */
@Data
@TableName("order_log")
public class OrderLog {
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_FINISH = "FINISH";
    public static final String ACTION_PASS = "PASS";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_URGE = "URGE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long operatorId;
    private String action;
    private String remark;
    private LocalDateTime createTime;
}
