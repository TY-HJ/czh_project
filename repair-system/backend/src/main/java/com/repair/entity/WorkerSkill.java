package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 维修工技能标签(维修工 与 报修类型 多对多)
 * 智能派单硬约束的数据基础: 候选维修工技能必须包含工单报修类型
 */
@Data
@TableName("worker_skill")
public class WorkerSkill {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workerId;
    private Long typeId;
}
