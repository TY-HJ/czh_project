package com.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.repair.audit.AuditLog;
import com.repair.common.BusinessException;
import com.repair.common.Result;
import com.repair.dto.PasswordDTO;
import com.repair.dto.ProfileDTO;
import com.repair.entity.User;
import com.repair.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人中心(全部角色): 资料查看/修改、修改密码(验证旧密码)
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 当前登录用户资料 */
    @GetMapping
    public Result<Map<String, Object>> get(Authentication auth) {
        User user = currentUser(auth);
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("phone", user.getPhone());
        info.put("role", user.getRole());
        info.put("avatar", user.getAvatar());
        info.put("createTime", user.getCreateTime());
        return Result.ok(info);
    }

    /** 修改个人资料(姓名、手机号) */
    @PutMapping
    @AuditLog(module = "个人中心", action = "修改个人资料")
    public Result<Void> update(@Valid @RequestBody ProfileDTO dto, Authentication auth) {
        User user = currentUser(auth);
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 修改密码: 必须验证旧密码 */
    @PutMapping("/password")
    @AuditLog(module = "个人中心", action = "修改密码")
    public Result<Void> password(@Valid @RequestBody PasswordDTO dto, Authentication auth) {
        User user = currentUser(auth);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        // 显式置空锁定计数(改密成功视为账号安全恢复)
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, passwordEncoder.encode(dto.getNewPassword()))
                .set(User::getLoginFailCount, 0)
                .set(User::getLockUntil, null));
        return Result.ok();
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }
}
