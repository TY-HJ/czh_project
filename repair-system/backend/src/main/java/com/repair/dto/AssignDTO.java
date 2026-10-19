package com.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员派单DTO */
@Data
public class AssignDTO {
    @NotNull(message = "请选择维修工")
    private Long workerId;
}
