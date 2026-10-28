package com.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.entity.DispatchRule;
import com.repair.entity.Message;
import com.repair.entity.RepairOrder;
import com.repair.entity.User;
import com.repair.mapper.MessageMapper;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.UserMapper;
import com.repair.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工单超时定时任务(每30分钟)
 * 规则:
 *   - 待派单超过24小时 -> 提醒管理员人工介入(兜底, 同一阶段只提醒一次)
 *   - 处理中超过 reassign_hours(默认48)小时 -> 触发智能改派(S11)
 *     改派成功后刷新派单时间, 不会重复触发; 达到改派上限后通知管理员人工介入
 *   - 待验收超过72小时 -> 提醒住户验收; 超过7天仍未验收 -> 提醒管理员介入
 *     只提醒不自动验收: 验收是住户的权利, 系统代为通过容易产生争议
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final RepairOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final DispatchService dispatchService;

    /** 完工后多少小时提醒住户验收 */
    private static final int ACCEPT_REMIND_HOURS = 72;
    /** 完工后多少小时仍未验收, 提醒管理员介入 */
    private static final int ACCEPT_ESCALATE_HOURS = 168;

    /** 已处理过的工单ID(避免同一工单在同一阶段重复发消息/重复改派) */
    private final Set<Long> pendingNotified = ConcurrentHashMap.newKeySet();
    private final Set<Long> processingHandled = ConcurrentHashMap.newKeySet();
    private final Set<Long> acceptanceReminded = ConcurrentHashMap.newKeySet();
    private final Set<Long> acceptanceEscalated = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void checkTimeout() {
        LocalDateTime now = LocalDateTime.now();
        DispatchRule rule = dispatchService.getRule();

        // 1. 待派单超24小时 -> 提醒管理员人工介入
        List<RepairOrder> pending = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_DISPATCH)
                .lt(RepairOrder::getSubmitTime, now.minusHours(24)));
        for (RepairOrder order : pending) {
            if (pendingNotified.add(order.getId())) {
                notifyAdmins("工单【" + order.getOrderNo() + "】已提交超过24小时仍未派单,请尽快处理");
            }
        }
        pendingNotified.removeIf(id -> pending.stream().noneMatch(o -> o.getId().equals(id)));

        // 2. 处理中超 reassign_hours(默认48小时) -> 自动改派
        List<RepairOrder> overdue = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .isNotNull(RepairOrder::getAssignTime)
                .lt(RepairOrder::getAssignTime, now.minusHours(rule.getReassignHours())));
        for (RepairOrder order : overdue) {
            // 同一工单同一派单时间只处理一次; 改派成功后 assignTime 刷新, 可在下一轮超时再次触发
            if (processingHandled.add(order.getId())) {
                try {
                    boolean reassigned = dispatchService.autoReassign(order);
                    if (reassigned) {
                        // 改派成功: 移出集合, 等新派单时间再次超时后可继续改派
                        processingHandled.remove(order.getId());
                    }
                    // 改派失败(达上限/无候选): 保留在集合中, 本轮派单周期内不再打扰
                } catch (Exception e) {
                    processingHandled.remove(order.getId());
                    log.warn("工单{}自动改派异常: {}", order.getOrderNo(), e.getMessage());
                }
            }
        }
        processingHandled.removeIf(id -> overdue.stream().noneMatch(o -> o.getId().equals(id)));

        // 3. 待验收超时 -> 先提醒住户, 再升级提醒管理员(不自动验收)
        List<RepairOrder> awaitingAccept = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_ACCEPTANCE)
                .isNotNull(RepairOrder::getFinishTime)
                .lt(RepairOrder::getFinishTime, now.minusHours(ACCEPT_REMIND_HOURS)));
        for (RepairOrder order : awaitingAccept) {
            // 3.1 完工超72小时: 提醒住户验收(同一张单只提醒一次)
            if (acceptanceReminded.add(order.getId())) {
                sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】已维修完成超过"
                        + (ACCEPT_REMIND_HOURS / 24) + "天,请尽快验收并评价,以便工单结案");
            }
            // 3.2 完工超7天仍未验收: 提醒管理员联系住户
            if (order.getFinishTime().isBefore(now.minusHours(ACCEPT_ESCALATE_HOURS))
                    && acceptanceEscalated.add(order.getId())) {
                notifyAdmins("工单【" + order.getOrderNo() + "】已完工超过" + (ACCEPT_ESCALATE_HOURS / 24)
                        + "天,住户仍未验收,请联系住户确认");
            }
        }
        acceptanceReminded.removeIf(id -> awaitingAccept.stream().noneMatch(o -> o.getId().equals(id)));
        acceptanceEscalated.removeIf(id -> awaitingAccept.stream().noneMatch(o -> o.getId().equals(id)));

        if (!pending.isEmpty() || !overdue.isEmpty() || !awaitingAccept.isEmpty()) {
            log.info("超时任务完成: 待派单超时{}单, 处理中超时{}单, 待验收超时{}单",
                    pending.size(), overdue.size(), awaitingAccept.size());
        }
    }

    /** 给指定用户发一条站内消息 */
    private void sendMessage(Long receiverId, String content) {
        Message msg = new Message();
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }

    private void notifyAdmins(String content) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN").eq(User::getStatus, 1));
        admins.forEach(a -> {
            Message msg = new Message();
            msg.setReceiverId(a.getId());
            msg.setContent(content);
            msg.setIsRead(0);
            messageMapper.insert(msg);
        });
    }
}
