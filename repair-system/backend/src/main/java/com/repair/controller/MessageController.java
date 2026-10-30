package com.repair.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.Result;
import com.repair.service.MessageService;
import com.repair.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 站内消息
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 我的消息: 分页 + 已读筛选(isRead 0未读/1已读, 不传查全部) */
    @GetMapping
    public Result<Page<MessageVO>> list(@RequestParam(required = false) Integer isRead,
                                        @RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        Authentication auth) {
        return Result.ok(messageService.myMessages((Long) auth.getPrincipal(), isRead, current, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(Authentication auth) {
        return Result.ok(messageService.unreadCount((Long) auth.getPrincipal()));
    }

    @PutMapping("/read-all")
    public Result<Void> readAll(Authentication auth) {
        messageService.markAllRead((Long) auth.getPrincipal());
        return Result.ok();
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id, Authentication auth) {
        messageService.markRead(id, (Long) auth.getPrincipal());
        return Result.ok();
    }

    /** 清空已读消息(未读保留), 返回删除条数。注意: 放在 /{id} 之前, 避免被当成 id */
    @DeleteMapping("/clear-read")
    public Result<Integer> clearRead(Authentication auth) {
        return Result.ok(messageService.clearRead((Long) auth.getPrincipal()));
    }

    /** 删除一条消息(只能删自己的) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication auth) {
        messageService.delete(id, (Long) auth.getPrincipal());
        return Result.ok();
    }
}
