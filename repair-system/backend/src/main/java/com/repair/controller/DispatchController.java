package com.repair.controller;

import com.repair.audit.AuditLog;
import com.repair.common.Result;
import com.repair.entity.DispatchRule;
import com.repair.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能派单接口(管理端): 派单弹窗候选列表 / 派单规则配置
 *
 * 派单动作本身走 OrderController 的 PUT /orders/{id}/assign, 这里不再重复提供入口
 */
@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DispatchController {

    private final DispatchService dispatchService;

    /**
     * 派单弹窗候选列表: 在岗维修工 + 技能匹配标记 + 实时得分。
     * 缺技能/已满负荷的人也会返回(带标记), 由管理员判断是否强制派单。
     */
    @GetMapping("/assign-candidates/{id}")
    public Result<List<Map<String, Object>>> assignCandidates(@PathVariable Long id) {
        return Result.ok(dispatchService.assignCandidates(id));
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
