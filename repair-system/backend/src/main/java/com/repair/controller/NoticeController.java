package com.repair.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.audit.AuditLog;
import com.repair.common.Result;
import com.repair.entity.Notice;
import com.repair.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 公告(所有人可看,管理员管理)
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/page")
    public Result<Page<Notice>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "10") long size) {
        return Result.ok(noticeService.page(current, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "公告管理", action = "发布公告")
    public Result<Void> add(@RequestBody Notice notice, Authentication auth) {
        noticeService.add(notice, (Long) auth.getPrincipal());
        return Result.ok();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "公告管理", action = "修改公告")
    public Result<Void> update(@RequestBody Notice notice) {
        noticeService.update(notice);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "公告管理", action = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.ok();
    }
}
