package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能派单规则(单行配置)
 * 综合得分 score = w_load*loadScore + w_rating*ratingScore + w_familiarity*famScore
 * 紧急工单负荷权重放大: w_load' = w_load * (1 + urgency_boost)
 */
@Data
@TableName("dispatch_rule")
public class DispatchRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Boolean autoDispatchEnabled;
    private BigDecimal wLoad;
    private BigDecimal wRating;
    private BigDecimal wFamiliarity;
    private BigDecimal urgencyBoost;
    private Integer maxLoad;
    private Integer familiarityFull;
    private BigDecimal defaultRating;
    private Integer reassignLimit;
    private Integer reassignHours;
}
