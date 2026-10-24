package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志(AOP切面自动记录)
 */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;        // 未登录(如登录失败)为空
    private String username;
    private String module;
    private String action;
    private String method;
    private String uri;
    private String params;      // 脱敏+截断后的参数摘要
    private String ip;
    private Integer success;    // 1成功 0失败
    private String errorMsg;
    private LocalDateTime createTime;
}
