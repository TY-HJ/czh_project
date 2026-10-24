package com.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.repair.entity.WorkerSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkerSkillMapper extends BaseMapper<WorkerSkill> {

    /** 全部技能标签(含类型名称, 供排行/派单展示) */
    @Select("SELECT ws.worker_id AS workerId, t.type_name AS typeName " +
            "FROM worker_skill ws JOIN repair_type t ON ws.type_id = t.id " +
            "ORDER BY ws.worker_id, t.sort")
    List<Map<String, Object>> listAllWithTypeName();
}
