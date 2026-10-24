package com.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.common.BusinessException;
import com.repair.common.Result;
import com.repair.dto.LoginDTO;
import com.repair.dto.RegisterDTO;
import com.repair.entity.User;
import com.repair.mapper.UserMapper;
import com.repair.service.UserService;
import com.repair.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping("/login")
    @com.repair.audit.AuditLog(module = "认证", action = "登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto.getUsername(), dto.getPassword()));
    }

    /** 仅住户可自行注册,维修工/管理员由管理员在后台添加 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    /** 获取当前登录用户信息(前端刷新页面后恢复状态用) */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("role", user.getRole());
        info.put("avatar", user.getAvatar());
        info.put("phone", user.getPhone());
        return Result.ok(info);
    }
}
