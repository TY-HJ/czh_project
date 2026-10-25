package com.repair.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单VO:在实体基础上补充关联名称,前端列表/详情直接用
 */
@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private String userName;
    private String userPhone;
    private Long typeId;
    private String typeName;
    private Long locationId;
    private String locationText;   // "1号楼 2层 201"
    private String title;
    private String description;
    private List<String> images;
    private Integer status;
    private String statusText;
    private Long workerId;
    private String workerName;
    private String priority;
    private Integer urgeCount;
    private String handleNote;
    private String dispatchType;      // AUTO-智能派单 MANUAL-手动派单
    private java.math.BigDecimal dispatchScore; // 派单时综合得分
    private Integer reassignCount;    // 自动改派次数
    /** 提交工单时的派单结果提示: 转人工时给出原因(仅提交接口返回, 不落库) */
    private String dispatchHint;
    private LocalDateTime workerAcceptTime; // 维修工接单时间
    private LocalDateTime submitTime;
    private LocalDateTime assignTime;
    private LocalDateTime finishTime;
    private LocalDateTime acceptTime;

    // 验收评价信息
    private Integer score;
    private String evaluationContent;
}
