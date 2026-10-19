package com.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 管理员新增/编辑用户DTO */
@Data
public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 新增时必填,编辑时留空表示不修改密码 */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "STUDENT|WORKER|ADMIN", message = "角色不合法")
    private String role;
}
