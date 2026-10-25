package com.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 维修工拒单DTO */
@Data
public class RefuseDTO {
    @NotBlank(message = "请填写拒单原因")
    @Size(max = 200, message = "拒单原因不能超过200字")
    private String reason;
}
