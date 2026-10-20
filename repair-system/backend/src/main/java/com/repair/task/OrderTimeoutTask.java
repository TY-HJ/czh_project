package com.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.entity.Message;
import com.repair.entity.RepairOrder;
import com.repair.entity.User;
import com.repair.mapper.MessageMapper;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工单超时催办定时任务
 * 规则:
 *   - 待派单超过24小时 -> 提醒所有管理员
 *   - 处理超过48小时 -> 提醒维修工和管理员
 * 每30分钟执行一次;同一工单在同一超时阶段只提醒一次,避免消息轰炸
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final RepairOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;

    /** 已提醒过的工单ID(待派单超时 / 处理中超时各一份),状态脱离该阶段后自动移除 */
    private final Set<Long> pendingNotified = ConcurrentHashMap.newKeySet();
    private final Set<Long> processingNotified = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void checkTimeout() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 待派单超24小时
        List<RepairOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_DISPATCH)
                .lt(RepairOrder::getSubmitTime, now.minusHours(24)));
        for (RepairOrder order : pending) {
            if (pendingNotified.add(order.getId())) {
                notifyAdmins("工单【" + order.getOrderNo() + "】已提交超过24小时仍未派单,请尽快处理");
            }
        }
        pendingNotified.removeIf(id -> pending.stream().noneMatch(o -> o.getId().equals(id)));

        // 2. 处理中超48小时
        List<RepairOrder> processing = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .lt(RepairOrder::getAssignTime, now.minusHours(48)));
        for (RepairOrder order : processing) {
            long hours = ChronoUnit.HOURS.between(order.getAssignTime(), now);
            if (processingNotified.add(order.getId())) {
                if (order.getWorkerId() != null) {
                    send(order.getWorkerId(), "工单【" + order.getOrderNo() + "】已处理超过48小时,请尽快完成");
                }
                notifyAdmins("工单【" + order.getOrderNo() + "】已派单" + hours + "小时未完成,请关注");
            }
        }
        processingNotified.removeIf(id -> processing.stream().noneMatch(o -> o.getId().equals(id)));

        if (!pending.isEmpty() || !processing.isEmpty()) {
            log.info("超时催办完成: 待派单超时{}单, 处理中超时{}单", pending.size(), processing.size());
        }
    }

    private void notifyAdmins(String content) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN").eq(User::getStatus, 1));
        admins.forEach(a -> send(a.getId(), content));
    }

    private void send(Long receiverId, String content) {
        Message msg = new Message();
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }
}
