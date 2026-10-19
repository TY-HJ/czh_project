package com.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.entity.Evaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /** 维修工评分排行 */
    @Select("SELECT u.real_name AS workerName, ROUND(AVG(e.score),1) AS avgScore, COUNT(*) AS orderCount " +
            "FROM evaluation e JOIN `user` u ON e.worker_id = u.id " +
            "GROUP BY e.worker_id, u.real_name ORDER BY avgScore DESC")
    List<Map<String, Object>> statWorkerRanking();
}
