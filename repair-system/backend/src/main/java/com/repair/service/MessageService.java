package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.entity.Message;
import com.repair.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内消息服务
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    public List<Message> myMessages(Long userId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .orderByDesc(Message::getCreateTime)
                .last("LIMIT 50"));
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId).eq(Message::getIsRead, 0));
    }

    public void markAllRead(Long userId) {
        Message update = new Message();
        update.setIsRead(1);
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId).eq(Message::getIsRead, 0));
    }

    public void markRead(Long id, Long userId) {
        Message msg = messageMapper.selectById(id);
        if (msg != null && msg.getReceiverId().equals(userId)) {
            msg.setIsRead(1);
            messageMapper.updateById(msg);
        }
    }
}
