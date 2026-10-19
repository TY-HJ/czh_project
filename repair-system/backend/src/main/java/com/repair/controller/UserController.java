package com.repair.controller;

import com.repair.common.Result;
import com.repair.dto.UserDTO;
import com.repair.entity.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理(仅管理员)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String role,
                                   @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(current, size, role, keyword));
    }

    /** 按角色查询用户列表(如派单下拉框) */
    @GetMapping("/role/{role}")
    public Result<List<User>> listByRole(@PathVariable String role) {
        return Result.ok(userService.listByRole(role));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody UserDTO dto) {
        userService.add(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
