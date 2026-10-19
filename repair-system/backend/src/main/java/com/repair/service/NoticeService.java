package com.repair.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.BusinessException;
import com.repair.entity.Notice;
import com.repair.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 公告服务
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public Page<Notice> page(long current, long size) {
        return noticeMapper.selectPage(new Page<>(current, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notice>()
                        .orderByDesc(Notice::getCreateTime));
    }

    public void add(Notice notice, Long adminId) {
        notice.setId(null);
        notice.setAdminId(adminId);
        noticeMapper.insert(notice);
    }

    public void update(Notice notice) {
        if (noticeMapper.selectById(notice.getId()) == null) {
            throw new BusinessException("公告不存在");
        }
        noticeMapper.updateById(notice);
    }

    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }
}
