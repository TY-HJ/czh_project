package com.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.audit.AuditLog;
import com.repair.common.BusinessException;
import com.repair.common.Result;
import com.repair.dto.SkillDTO;
import com.repair.dto.UserDTO;
import com.repair.entity.User;
import com.repair.entity.WorkerSkill;
import com.repair.mapper.UserMapper;
import com.repair.mapper.WorkerSkillMapper;
import com.repair.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理(仅管理员) + 维修工技能标签维护(S11)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final WorkerSkillMapper workerSkillMapper;

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
    @AuditLog(module = "用户管理", action = "新增用户")
    public Result<Void> add(@Valid @RequestBody UserDTO dto) {
        userService.add(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @AuditLog(module = "用户管理", action = "编辑用户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/toggle-status")
    @AuditLog(module = "用户管理", action = "启用/禁用用户")
    public Result<Void> toggleStatus(@PathVariable Long id, Authentication auth) {
        userService.toggleStatus(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = "用户管理", action = "删除用户")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        userService.delete(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    // ==================== 维修工技能标签(S11) ====================

    /** 查询维修工的技能(报修类型ID列表) */
    @GetMapping("/{id}/skills")
    public Result<List<Long>> getSkills(@PathVariable Long id) {
        checkWorker(id);
        return Result.ok(workerSkillMapper.selectList(new LambdaQueryWrapper<WorkerSkill>()
                        .eq(WorkerSkill::getWorkerId, id))
                .stream().map(WorkerSkill::getTypeId).collect(Collectors.toList()));
    }

    /** 全量覆盖保存维修工技能标签 */
    @PutMapping("/{id}/skills")
    @Transactional
    @AuditLog(module = "用户管理", action = "维护维修工技能标签")
    public Result<Void> saveSkills(@PathVariable Long id, @RequestBody SkillDTO dto) {
        checkWorker(id);
        workerSkillMapper.delete(new LambdaQueryWrapper<WorkerSkill>()
                .eq(WorkerSkill::getWorkerId, id));
        if (dto.getTypeIds() != null) {
            for (Long typeId : dto.getTypeIds().stream().distinct().toList()) {
                WorkerSkill ws = new WorkerSkill();
                ws.setWorkerId(id);
                ws.setTypeId(typeId);
                workerSkillMapper.insert(ws);
            }
        }
        return Result.ok();
    }

    private void checkWorker(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"WORKER".equals(user.getRole())) {
            throw new BusinessException("仅维修工可配置技能标签");
        }
    }
}
