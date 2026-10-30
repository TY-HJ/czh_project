package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.entity.RepairOrder;
import com.repair.entity.RepairType;
import com.repair.mapper.EvaluationMapper;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.RepairTypeMapper;
import com.repair.mapper.WorkerSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务:为管理端 ECharts 图表提供数据
 * V2.0 新增: 楼栋×单元热力图、月度趋势环比预警、高发类型预警、排行附技能标签
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final RepairOrderMapper orderMapper;
    private final EvaluationMapper evaluationMapper;
    private final WorkerSkillMapper workerSkillMapper;
    private final RepairTypeMapper typeMapper;

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
        // 已关闭也必须有计数: 否则"总数"比下面各状态之和多, 大屏数字对不上
        result.put("closed", orderMapper.selectCount(
                new LambdaQueryWrapper<RepairOrder>().eq(RepairOrder::getStatus, 4)));

        // 平均处理时长:完成工单的 (完成时间-提交时间) 平均小时数
        List<RepairOrder> completed = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, 3)
                .isNotNull(RepairOrder::getFinishTime));
        double avg = completed.stream()
                .mapToLong(o -> ChronoUnit.HOURS.between(o.getSubmitTime(), o.getFinishTime()))
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

    /** 维修工评分排行, 附技能标签(S12) */
    public List<Map<String, Object>> workerRanking() {
        List<Map<String, Object>> rows = evaluationMapper.statWorkerRanking();
        if (rows.isEmpty()) {
            return rows;
        }
        // workerId -> "水电维修、电梯故障"
        Map<Object, String> skills = workerSkillMapper.listAllWithTypeName().stream()
                .collect(Collectors.groupingBy(m -> m.get("workerId"),
                        Collectors.mapping(m -> String.valueOf(m.get("typeName")), Collectors.joining("、"))));
        rows.forEach(r -> r.put("skills", skills.getOrDefault(r.get("workerId"), "")));
        return rows;
    }

    /** 楼栋×单元报修热力数据(S12大屏) */
    public List<Map<String, Object>> heatmap() {
        return orderMapper.statBuildingHeatmap();
    }

    /**
     * 月度趋势环比预警 + 高发类型预警(S12)
     * 预警规则: 环比增长>50% 且 当月单量>=3 标红; 本月Top1类型占比>40% 输出告警
     */
    public Map<String, Object> trendWarning() {
        List<Map<String, Object>> trend = orderMapper.statMonthlyTrend();
        List<Map<String, Object>> months = new ArrayList<>();
        long prev = 0;
        for (Map<String, Object> row : trend) {
            long count = ((Number) row.get("count")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", row.get("month"));
            m.put("count", count);
            if (prev > 0) {
                double growth = (count - prev) * 100.0 / prev;
                m.put("growth", Math.round(growth * 10) / 10.0);
                // 环比增长>50% 且绝对量>=3 -> 预警
                m.put("warning", growth > 50 && count >= 3);
            } else {
                m.put("growth", null);
                m.put("warning", false);
            }
            prev = count;
            months.add(m);
        }

        // 本月高发类型
        LocalDate now = LocalDate.now();
        List<RepairOrder> monthOrders = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .ge(RepairOrder::getSubmitTime, now.withDayOfMonth(1).atStartOfDay()));

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("topType", topType(monthOrders, monthOrders.size()));
        return result;
    }

    /** 本月报修量Top1类型及占比; 占比>40% 触发告警 */
    private Map<String, Object> topType(List<RepairOrder> monthOrders, int total) {
        if (monthOrders.isEmpty()) {
            return null;
        }
        Map<Long, Long> byType = monthOrders.stream()
                .collect(Collectors.groupingBy(RepairOrder::getTypeId, Collectors.counting()));
        return byType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    long count = e.getValue();
                    double ratio = count * 100.0 / total;
                    m.put("typeId", e.getKey());
                    RepairType type = typeMapper.selectById(e.getKey());
                    m.put("typeName", type == null ? "未知类型" : type.getTypeName());
                    m.put("count", count);
                    m.put("ratio", Math.round(ratio * 10) / 10.0);
                    m.put("warning", ratio > 40);
                    return m;
                }).orElse(null);
    }
}
