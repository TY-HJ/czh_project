package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.entity.RepairOrder;
import com.repair.mapper.EvaluationMapper;
import com.repair.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务:为管理端 ECharts 图表提供数据
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final RepairOrderMapper orderMapper;
    private final EvaluationMapper evaluationMapper;

    /** 总览卡片:各状态数量 + 平均处理时长(小时) */
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        result.put("total", orderMapper.selectCount(null));
        result.put("pendingDispatch", orderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, 0)));
        result.put("processing", orderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, 1)));
        result.put("pendingAcceptance", orderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, 2)));
        result.put("completed", orderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, 3)));

        // 平均处理时长:完成工单的 (完成时间-提交时间) 平均小时数
        List<RepairOrder> completed = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, 3)
                .isNotNull(RepairOrder::getFinishTime));
        double avg = completed.stream()
                .mapToLong(o -> java.time.temporal.ChronoUnit.HOURS.between(o.getSubmitTime(), o.getFinishTime()))
                .average().orElse(0);
        result.put("avgDurationHours", Math.round(avg * 10) / 10.0);
        return result;
    }

    public List<Map<String, Object>> typeDistribution() {
        return orderMapper.statTypeDistribution();
    }

    public List<Map<String, Object>> monthlyTrend() {
        return orderMapper.statMonthlyTrend();
    }

    public List<Map<String, Object>> workerRanking() {
        return evaluationMapper.statWorkerRanking();
    }
}
