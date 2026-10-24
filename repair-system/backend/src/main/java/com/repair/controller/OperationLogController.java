package com.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.Result;
import com.repair.entity.OperationLog;
import com.repair.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 操作日志查询(管理端审计页)
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogMapper logMapper;

    /** 分页查询, 支持按操作人/模块/日期区间筛选 */
    @GetMapping("/page")
    public Result<Page<OperationLog>> page(@RequestParam(required = false) String username,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (startDate != null) {
            wrapper.ge(OperationLog::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(OperationLog::getCreateTime, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return Result.ok(logMapper.selectPage(new Page<>(current, size), wrapper));
    }
}
