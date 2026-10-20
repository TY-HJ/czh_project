package com.repair.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.Result;
import com.repair.dto.AcceptanceDTO;
import com.repair.dto.AssignDTO;
import com.repair.dto.FinishDTO;
import com.repair.dto.OrderCreateDTO;
import com.repair.service.OrderService;
import com.repair.vo.LogVO;
import com.repair.vo.OrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工单接口(按角色区分权限)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== 提交(住户) ====================
    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<Void> create(@Valid @RequestBody OrderCreateDTO dto, Authentication auth) {
        orderService.create((Long) auth.getPrincipal(), dto);
        return Result.ok();
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
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignDTO dto, Authentication auth) {
        orderService.assign(id, (Long) auth.getPrincipal(), dto.getWorkerId());
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
    public Result<Void> acceptance(@PathVariable Long id, @Valid @RequestBody AcceptanceDTO dto, Authentication auth) {
        orderService.acceptance(id, (Long) auth.getPrincipal(), dto);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<Void> cancel(@PathVariable Long id, Authentication auth) {
        orderService.cancel(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
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

    // ==================== 详情与日志 ====================
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @GetMapping("/{id}/logs")
    public Result<List<LogVO>> logs(@PathVariable Long id) {
        return Result.ok(orderService.logs(id));
    }
}
