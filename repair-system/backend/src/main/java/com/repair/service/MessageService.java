package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.BusinessException;
import com.repair.entity.Message;
import com.repair.entity.RepairOrder;
import com.repair.mapper.MessageMapper;
import com.repair.mapper.RepairOrderMapper;
import com.repair.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 站内消息服务:
 * 支持分页与已读筛选, 并从消息文案中的【工单号】解析关联工单,
 * 使前端可点击消息直达工单详情
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final RepairOrderMapper orderMapper;

    /** 消息文案中的工单号格式: 【202610230001】(yyyyMMdd + 4位流水) */
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("【(\\d{12})】");

    /** 我的消息(分页 + 已读筛选) */
    public Page<MessageVO> myMessages(Long userId, Integer isRead, long current, long size) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getReceiverId, userId);
        if (isRead != null) {
            wrapper.eq(Message::getIsRead, isRead);
        }
        wrapper.orderByDesc(Message::getCreateTime);
        Page<Message> page = messageMapper.selectPage(new Page<>(current, size), wrapper);

        // 批量解析文案中的工单号 -> 回查工单ID, 避免逐条查库
        List<String> orderNos = page.getRecords().stream()
                .map(m -> extractOrderNo(m.getContent()))
                .filter(no -> no != null)
                .distinct()
                .collect(Collectors.toList());
        Map<String, RepairOrder> orderMap = orderNos.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                        .in(RepairOrder::getOrderNo, orderNos))
                        .stream()
                        .collect(Collectors.toMap(RepairOrder::getOrderNo, Function.identity()));

        Page<MessageVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setContent(m.getContent());
            vo.setIsRead(m.getIsRead());
            vo.setCreateTime(m.getCreateTime());
            String orderNo = extractOrderNo(m.getContent());
            if (orderNo != null) {
                vo.setOrderNo(orderNo);
                RepairOrder order = orderMap.get(orderNo);
                vo.setOrderId(order == null ? null : order.getId());
            }
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    /** 从消息文案中提取工单号, 无则返回 null */
    private String extractOrderNo(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = ORDER_NO_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
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

    /** 删除一条消息(只能删自己的); 已删除时静默返回, 保证重复点击不报错 */
    public void delete(Long id, Long userId) {
        Message msg = messageMapper.selectById(id);
        if (msg == null) {
            return;
        }
        if (!msg.getReceiverId().equals(userId)) {
            throw new BusinessException("无权删除该消息");
        }
        messageMapper.deleteById(id);
    }

    /** 清空自己的已读消息, 未读保留; 返回删除条数 */
    public int clearRead(Long userId) {
        return messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 1));
    }
}
