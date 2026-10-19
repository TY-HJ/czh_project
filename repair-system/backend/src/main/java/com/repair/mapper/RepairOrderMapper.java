package com.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 报修类型分布统计 */
    @Select("SELECT t.type_name AS name, COUNT(*) AS value " +
            "FROM repair_order o JOIN repair_type t ON o.type_id = t.id " +
            "GROUP BY t.type_name ORDER BY value DESC")
    List<Map<String, Object>> statTypeDistribution();

    /** 近6个月工单量趋势 */
    @Select("SELECT DATE_FORMAT(submit_time, '%Y-%m') AS month, COUNT(*) AS count " +
            "FROM repair_order WHERE submit_time >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
            "GROUP BY month ORDER BY month")
    List<Map<String, Object>> statMonthlyTrend();
}
