package com.repair.vo;

import lombok.Data;

/**
 * 智能派单候选人得分明细
 * 各维度已按派单规则归一化至 [0,1]
 */
@Data
public class CandidateVO {
    private Long workerId;
    private String workerName;
    private String phone;
    private Integer currentLoad;    // 当前未完成工单数 L
    private Double loadScore;       // 负荷分 1 - min(L/maxLoad,1)
    private Double ratingScore;     // 历史评分分 avg/5 或 default_rating
    private Double avgRating;       // 原始平均评分(1~5), 无评价为null
    private Integer familiarCount;  // 该楼栋历史完成工单数 C
    private Double famScore;        // 楼栋熟悉度分 min(C/full,1)
    private Double totalScore;      // 综合得分
}
