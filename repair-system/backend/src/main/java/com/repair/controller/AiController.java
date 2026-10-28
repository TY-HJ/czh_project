package com.repair.controller;

import com.repair.common.BusinessException;
import com.repair.common.Result;
import com.repair.dto.AiTextDTO;
import com.repair.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 辅助填写(需登录): 只返回文字草稿, 不落库、不改变任何工单状态。
 * 前端拿到的永远是"可编辑的草稿", 是否采用由用户决定。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** 住户: 根据类型/位置/标题/关键词生成报修描述草稿 */
    @PostMapping("/order-description")
    public Result<String> orderDescription(@RequestBody AiTextDTO dto) {
        return Result.ok(aiService.orderDescription(dto));
    }

    /** 维修工: 根据工单内容与维修耗时生成完工说明草稿 */
    @PostMapping("/finish-note")
    public Result<String> finishNote(@RequestBody AiTextDTO dto, Authentication auth) {
        return Result.ok(aiService.finishNote(dto.getOrderId(), currentUserId(auth), dto.getKeywords()));
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new BusinessException("未登录");
        }
        return userId;
    }
}
