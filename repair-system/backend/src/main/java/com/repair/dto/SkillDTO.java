package com.repair.dto;

import lombok.Data;

import java.util.List;

/**
 * 维修工技能标签维护入参(报修类型ID集合, 全量覆盖)
 */
@Data
public class SkillDTO {
    private List<Long> typeIds;
}
