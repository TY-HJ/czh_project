package com.repair.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生验收DTO:pass=true 必须打分;pass=false 填不通过原因
 */
@Data
public class AcceptanceDTO {
    @NotNull(message = "请选择验收结果")
    private Boolean pass;

    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer score;

    private String content;   // 评语 / 不通过原因
}
