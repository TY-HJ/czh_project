package com.repair.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员派单DTO */
@Data
public class AssignDTO {
    @NotNull(message = "请选择维修工")
    private Long workerId;

    /**
     * 强制派单: 该师傅未配置本工单类型的技能时, 管理员二次确认后置 true。
     * 用于避免"该类型只有一个人会修、他又被禁用"导致工单永久派不出去。
     */
    private Boolean force;
}
