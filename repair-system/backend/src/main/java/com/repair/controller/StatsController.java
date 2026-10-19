package com.repair.controller;

import com.repair.common.Result;
import com.repair.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口(管理端大屏)
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public Result<java.util.Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/type-distribution")
    public Result<java.util.List<java.util.Map<String, Object>>> typeDistribution() {
        return Result.ok(statsService.typeDistribution());
    }

    @GetMapping("/monthly-trend")
    public Result<java.util.List<java.util.Map<String, Object>>> monthlyTrend() {
        return Result.ok(statsService.monthlyTrend());
    }

    @GetMapping("/worker-ranking")
    public Result<java.util.List<java.util.Map<String, Object>>> workerRanking() {
        return Result.ok(statsService.workerRanking());
    }
}
