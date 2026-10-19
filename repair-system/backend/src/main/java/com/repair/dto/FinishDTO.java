package com.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 维修工完成工单DTO */
@Data
public class FinishDTO {
    @NotBlank(message = "请填写处理说明")
    private String handleNote;
}
