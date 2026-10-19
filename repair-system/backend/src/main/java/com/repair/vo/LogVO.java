package com.repair.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 工单流转日志VO(带操作人姓名) */
@Data
public class LogVO {
    private Long id;
    private String operatorName;
    private String action;      // 原始动作
    private String actionText;  // 中文动作
    private String remark;
    private LocalDateTime createTime;
}
