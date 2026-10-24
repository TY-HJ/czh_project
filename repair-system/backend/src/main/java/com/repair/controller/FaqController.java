package com.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.audit.AuditLog;
import com.repair.common.BusinessException;
import com.repair.common.Result;
import com.repair.entity.Faq;
import com.repair.entity.RepairType;
import com.repair.mapper.FaqMapper;
import com.repair.mapper.RepairTypeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库FAQ: 全角色浏览(分类/关键字/热度推荐), 管理员维护
 */
@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqMapper faqMapper;
    private final RepairTypeMapper typeMapper;

    /** FAQ 分页浏览(登录用户), 按分类/关键字过滤 */
    @GetMapping
    public Result<Page<Faq>> page(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") long current,
                                  @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Faq::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Faq::getTitle, keyword).or().like(Faq::getContent, keyword));
        }
        wrapper.orderByAsc(Faq::getSort).orderByDesc(Faq::getViewCount);
        return Result.ok(faqMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 报修页推荐: 按已选报修类型推荐相关FAQ(未选类型时返回浏览热度Top5)
     */
    @GetMapping("/recommend")
    public Result<List<Faq>> recommend(@RequestParam(required = false) Long typeId) {
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<>();
        if (typeId != null) {
            RepairType type = typeMapper.selectById(typeId);
            if (type != null) {
                wrapper.eq(Faq::getCategory, type.getTypeName());
                wrapper.orderByAsc(Faq::getSort);
                wrapper.last("LIMIT 5");
                return Result.ok(faqMapper.selectList(wrapper));
            }
        }
        // 未选类型: 浏览热度Top5
        wrapper.orderByDesc(Faq::getViewCount);
        wrapper.last("LIMIT 5");
        return Result.ok(faqMapper.selectList(wrapper));
    }

    /** 浏览计数(点开条目时调用) */
    @PutMapping("/{id}/view")
    public Result<Void> view(@PathVariable Long id) {
        faqMapper.update(null, new LambdaUpdateWrapper<Faq>()
                .eq(Faq::getId, id)
                .setSql("view_count = view_count + 1"));
        return Result.ok();
    }

    // ==================== 管理员维护 ====================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "知识库FAQ", action = "新增FAQ")
    public Result<Void> add(@Valid @RequestBody Faq faq, Authentication auth) {
        faq.setId(null);
        faq.setViewCount(0);
        faq.setAdminId((Long) auth.getPrincipal());
        faqMapper.insert(faq);
        return Result.ok();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "知识库FAQ", action = "修改FAQ")
    public Result<Void> update(@Valid @RequestBody Faq faq) {
        if (faqMapper.selectById(faq.getId()) == null) {
            throw new BusinessException("FAQ不存在");
        }
        faqMapper.updateById(faq);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(module = "知识库FAQ", action = "删除FAQ")
    public Result<Void> delete(@PathVariable Long id) {
        faqMapper.deleteById(id);
        return Result.ok();
    }
}
