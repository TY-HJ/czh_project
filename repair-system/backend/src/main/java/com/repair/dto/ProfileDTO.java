package com.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 个人资料修改入参(姓名、手机号)
 */
@Data
public class ProfileDTO {
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
}
