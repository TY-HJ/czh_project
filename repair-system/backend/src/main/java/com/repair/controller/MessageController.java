package com.repair.controller;

import com.repair.common.Result;
import com.repair.entity.Message;
import com.repair.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内消息
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public Result<List<Message>> list(Authentication auth) {
        return Result.ok(messageService.myMessages((Long) auth.getPrincipal()));
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
}
