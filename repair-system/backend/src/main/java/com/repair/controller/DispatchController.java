package com.repair.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.audit.AuditLog;
import com.repair.common.Result;
import com.repair.dto.AssignDTO;
import com.repair.entity.DispatchRule;
import com.repair.service.DispatchService;
import com.repair.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能派单接口(管理端): 候选人得分明细 / 手动重派兜底 / 派单规则配置
 */
@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DispatchController {

    private final DispatchService dispatchService;
    private final OrderService orderService;

    /**
     * 候选人得分明细:
     * 待派单工单返回实时打分排序; 已派单工单回读存档明细("当时为什么选他")
     */
    @GetMapping("/candidates/{id}")
    public Result<List<Map<String, Object>>> candidates(@PathVariable Long id) {
        return Result.ok(dispatchService.candidatesFor(id));
    }

    /** 手动重派(兜底): 记 MANUAL */
    @PostMapping("/manual/{id}")
    @AuditLog(module = "智能派单", action = "手动派单/重派")
    public Result<Void> manual(@PathVariable Long id, @RequestBody AssignDTO dto, Authentication auth) {
        orderService.assign(id, (Long) auth.getPrincipal(), dto.getWorkerId());
        return Result.ok();
    }

    /** 派单规则查询(开关/权重/参数) */
    @GetMapping("/rule")
    public Result<DispatchRule> rule() {
        return Result.ok(dispatchService.getRule());
    }

    /** 派单规则保存(校验权重∈[0,1]且总和>0) */
    @PutMapping("/rule")
    @AuditLog(module = "智能派单", action = "修改派单规则")
    public Result<Void> saveRule(@RequestBody DispatchRule dto) {
        dispatchService.saveRule(dto);
        return Result.ok();
    }
}
