package com.repair.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.audit.AuditLog;
import com.repair.common.Result;
import com.repair.dto.AcceptanceDTO;
import com.repair.dto.AssignDTO;
import com.repair.dto.FinishDTO;
import com.repair.dto.OrderCreateDTO;
import com.repair.dto.RefuseDTO;
import com.repair.service.OrderService;
import com.repair.vo.LogVO;
import com.repair.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单接口(按角色区分权限)
 * 智能派单候选/规则接口在 DispatchController
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== 提交(住户, 触发智能派单) ====================
    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    @AuditLog(module = "工单管理", action = "提交报修")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto, Authentication auth) {
        return Result.ok(orderService.create((Long) auth.getPrincipal(), dto));
    }

    // ==================== 列表查询 ====================
    @GetMapping("/my")
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<Page<OrderVO>> my(@RequestParam(required = false) Integer status,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size,
                                    Authentication auth) {
        return Result.ok(orderService.myOrders((Long) auth.getPrincipal(), status, current, size));
    }

    @GetMapping("/worker")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Page<OrderVO>> workerOrders(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              Authentication auth) {
        return Result.ok(orderService.workerOrders((Long) auth.getPrincipal(), status, current, size));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<OrderVO>> adminPage(@RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.adminOrders(status, keyword, current, size));
    }

    // ==================== 状态流转 ====================
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "工单管理", action = "派单/改派")
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignDTO dto, Authentication auth) {
        orderService.assign(id, (Long) auth.getPrincipal(), dto.getWorkerId(), dto.getForce());
        return Result.ok();
    }

    /** 维修工接单确认(S11): 记录接单时间, 通知住户 */
    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> accept(@PathVariable Long id, Authentication auth) {
        orderService.accept(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    /** 维修工拒单: 未接单前可拒, 必填原因, 工单退回待派单 */
    @PutMapping("/{id}/refuse")
    @PreAuthorize("hasRole('WORKER')")
    @AuditLog(module = "工单管理", action = "维修工拒单")
    public Result<Void> refuse(@PathVariable Long id, @Valid @RequestBody RefuseDTO dto, Authentication auth) {
        orderService.refuse(id, (Long) auth.getPrincipal(), dto.getReason());
        return Result.ok();
    }

    @PutMapping("/{id}/finish")
    @PreAuthorize("hasRole('WORKER')")
    public Result<Void> finish(@PathVariable Long id, @Valid @RequestBody FinishDTO dto, Authentication auth) {
        orderService.finish(id, (Long) auth.getPrincipal(), dto.getHandleNote());
        return Result.ok();
    }

    @PutMapping("/{id}/acceptance")
    @PreAuthorize("hasRole('RESIDENT')")
    @AuditLog(module = "工单管理", action = "验收工单")
    public Result<Void> acceptance(@PathVariable Long id, @Valid @RequestBody AcceptanceDTO dto, Authentication auth) {
        orderService.acceptance(id, (Long) auth.getPrincipal(), dto);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('RESIDENT')")
    @AuditLog(module = "工单管理", action = "撤销工单")
    public Result<Void> cancel(@PathVariable Long id, Authentication auth) {
        orderService.cancel(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "工单管理", action = "关闭工单")
    public Result<Void> close(@PathVariable Long id, Authentication auth) {
        orderService.close(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    /** 催办(报修人或管理员) */
    @PutMapping("/{id}/urge")
    public Result<Void> urge(@PathVariable Long id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        orderService.urge(id, (Long) auth.getPrincipal(), isAdmin);
        return Result.ok();
    }

    // ==================== 详情与日志(带归属校验) ====================
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return Result.ok(orderService.detail(id, (Long) auth.getPrincipal(), role));
    }

    @GetMapping("/{id}/logs")
    public Result<List<LogVO>> logs(@PathVariable Long id, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return Result.ok(orderService.logs(id, (Long) auth.getPrincipal(), role));
    }
}
