package com.repair.vo;

import lombok.Data;

/** 登录成功返回:token + 用户信息 */
@Data
public class LoginVO {
    private String token;
    private Long id;
    private String username;
    private String realName;
    private String role;
    private String avatar;
}
