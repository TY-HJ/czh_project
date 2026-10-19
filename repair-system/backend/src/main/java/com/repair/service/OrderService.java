package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.BusinessException;
import com.repair.dto.AcceptanceDTO;
import com.repair.dto.OrderCreateDTO;
import com.repair.entity.*;
import com.repair.mapper.*;
import com.repair.vo.LogVO;
import com.repair.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单服务 —— 状态机核心
 *
 * 状态流转图:
 *   [0 待派单] --管理员派单--> [1 处理中] --维修工完成--> [2 待验收]
 *        |                        ^    --验收不通过-退回---||
 *        |--学生撤销/管理员关闭--> [4 已关闭]        [2] --验收通过--> [3 已完成]
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RepairOrderMapper orderMapper;
    private final OrderLogMapper logMapper;
    private final EvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final RepairTypeMapper typeMapper;
    private final LocationMapper locationMapper;
    private final MessageMapper messageMapper;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 学生:提交报修 ====================
    @Transactional
    public void create(Long userId, OrderCreateDTO dto) {
        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTypeId(dto.getTypeId());
        order.setLocationId(dto.getLocationId());
        order.setTitle(dto.getTitle());
        order.setDescription(dto.getDescription());
        order.setImages(dto.getImages());
        order.setStatus(RepairOrder.STATUS_PENDING_DISPATCH);
        order.setPriority(StringUtils.hasText(dto.getPriority()) ? dto.getPriority() : "NORMAL");
        order.setUrgeCount(0);
        orderMapper.insert(order);

        addLog(order.getId(), userId, OrderLog.ACTION_SUBMIT, "提交报修");
        // 通知所有管理员有新工单
        notifyRole("ADMIN", "新报修工单【" + order.getOrderNo() + "】" + order.getTitle() + ",请及时派单");
    }

    /** 工单号规则: yyyyMMdd + 当天第几个(4位) */
    private String generateOrderNo() {
        String today = LocalDate.now().format(NO_FMT);
        Long todayCount = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .likeRight(RepairOrder::getOrderNo, today));
        return today + String.format("%04d", todayCount + 1);
    }

    // ==================== 管理员:派单 ====================
    @Transactional
    public void assign(Long orderId, Long adminId, Long workerId) {
        RepairOrder order = getOrder(orderId);
        checkStatus(order, RepairOrder.STATUS_PENDING_DISPATCH, "该工单已派单,无需重复操作");
        if (workerId == null) {
            throw new BusinessException("请选择维修工");
        }
        User worker = userMapper.selectById(workerId);
        if (worker == null || !"WORKER".equals(worker.getRole())) {
            throw new BusinessException("维修工不存在");
        }
        order.setStatus(RepairOrder.STATUS_PROCESSING);
        order.setWorkerId(workerId);
        order.setAdminId(adminId);
        order.setAssignTime(LocalDateTime.now());
        orderMapper.updateById(order);

        addLog(orderId, adminId, OrderLog.ACTION_ASSIGN, "派单给 " + worker.getRealName());
        sendMessage(workerId, "您有新的维修任务【" + order.getOrderNo() + "】" + order.getTitle() + ",请及时处理");
    }

    // ==================== 维修工:完成维修 ====================
    @Transactional
    public void finish(Long orderId, Long workerId, String handleNote) {
        RepairOrder order = getOrder(orderId);
        checkOwner(order, workerId, "只有该工单的维修工才能操作");
        checkStatus(order, RepairOrder.STATUS_PROCESSING, "工单当前状态不可标记完成");
        order.setStatus(RepairOrder.STATUS_PENDING_ACCEPTANCE);
        order.setHandleNote(handleNote);
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);

        addLog(orderId, workerId, OrderLog.ACTION_FINISH, handleNote);
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】已维修完成,请及时验收");
    }

    // ==================== 学生:验收(通过/不通过) ====================
    @Transactional
    public void acceptance(Long orderId, Long userId, AcceptanceDTO dto) {
        RepairOrder order = getOrder(orderId);
        checkOwner(order, userId, "只有报修人才能验收");
        checkStatus(order, RepairOrder.STATUS_PENDING_ACCEPTANCE, "工单当前不在待验收状态");

        if (Boolean.TRUE.equals(dto.getPass())) {
            if (dto.getScore() == null) {
                throw new BusinessException("验收通过必须打分");
            }
            order.setStatus(RepairOrder.STATUS_COMPLETED);
            order.setAcceptTime(LocalDateTime.now());
            orderMapper.updateById(order);

            Evaluation evaluation = new Evaluation();
            evaluation.setOrderId(orderId);
            evaluation.setWorkerId(order.getWorkerId());
            evaluation.setUserId(userId);
            evaluation.setScore(dto.getScore());
            evaluation.setContent(dto.getContent());
            evaluationMapper.insert(evaluation);

            addLog(orderId, userId, OrderLog.ACTION_PASS, "验收通过,评分" + dto.getScore() + "星");
            if (order.getWorkerId() != null) {
                sendMessage(order.getWorkerId(), "工单【" + order.getOrderNo() + "】已验收通过,获" + dto.getScore() + "星评价");
            }
        } else {
            // 验收不通过:退回处理中,维修工需重新处理
            order.setStatus(RepairOrder.STATUS_PROCESSING);
            order.setFinishTime(null);
            orderMapper.updateById(order);

            addLog(orderId, userId, OrderLog.ACTION_REJECT,
                    "验收不通过:" + (StringUtils.hasText(dto.getContent()) ? dto.getContent() : "未说明原因"));
            if (order.getWorkerId() != null) {
                sendMessage(order.getWorkerId(), "工单【" + order.getOrderNo() + "】验收不通过,请重新处理");
            }
        }
    }

    // ==================== 学生:撤销(仅待派单可撤) ====================
    @Transactional
    public void cancel(Long orderId, Long userId) {
        RepairOrder order = getOrder(orderId);
        checkOwner(order, userId, "只有报修人才能撤销");
        checkStatus(order, RepairOrder.STATUS_PENDING_DISPATCH, "工单已派单,如需处理请联系管理员关闭");
        order.setStatus(RepairOrder.STATUS_CLOSED);
        orderMapper.updateById(order);
        addLog(orderId, userId, OrderLog.ACTION_CANCEL, "报修人撤销工单");
    }

    // ==================== 管理员:强制关闭 ====================
    @Transactional
    public void close(Long orderId, Long adminId) {
        RepairOrder order = getOrder(orderId);
        if (order.getStatus() == RepairOrder.STATUS_COMPLETED || order.getStatus() == RepairOrder.STATUS_CLOSED) {
            throw new BusinessException("该工单已完成或已关闭");
        }
        order.setStatus(RepairOrder.STATUS_CLOSED);
        orderMapper.updateById(order);
        addLog(orderId, adminId, OrderLog.ACTION_CLOSE, "管理员关闭工单");
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】已被管理员关闭");
    }

    // ==================== 催办 ====================
    @Transactional
    public void urge(Long orderId, Long userId) {
        RepairOrder order = getOrder(orderId);
        if (order.getStatus() != RepairOrder.STATUS_PENDING_DISPATCH
                && order.getStatus() != RepairOrder.STATUS_PROCESSING) {
            throw new BusinessException("工单当前状态无需催办");
        }
        order.setUrgeCount(order.getUrgeCount() + 1);
        orderMapper.updateById(order);
        addLog(orderId, userId, OrderLog.ACTION_URGE, "第" + order.getUrgeCount() + "次催办");
        // 通知管理员和维修工
        notifyRole("ADMIN", "工单【" + order.getOrderNo() + "】被催办(第" + order.getUrgeCount() + "次),请关注处理进度");
        if (order.getWorkerId() != null) {
            sendMessage(order.getWorkerId(), "工单【" + order.getOrderNo() + "】被催办,请尽快处理");
        }
    }

    // ==================== 查询 ====================

    /** 学生的工单列表 */
    public Page<OrderVO> myOrders(Long userId, Integer status, long current, long size) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepairOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(RepairOrder::getStatus, status);
        }
        wrapper.orderByDesc(RepairOrder::getSubmitTime);
        return convertPage(orderMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /** 维修工的任务列表 */
    public Page<OrderVO> workerOrders(Long workerId, Integer status, long current, long size) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepairOrder::getWorkerId, workerId);
        if (status != null) {
            wrapper.eq(RepairOrder::getStatus, status);
        } else {
            // 默认显示未完结的
            wrapper.in(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING, RepairOrder.STATUS_PENDING_ACCEPTANCE);
        }
        wrapper.orderByDesc(RepairOrder::getSubmitTime);
        return convertPage(orderMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /** 管理员的工单列表(全部) */
    public Page<OrderVO> adminOrders(Integer status, String keyword, long current, long size) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(RepairOrder::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RepairOrder::getOrderNo, keyword).or().like(RepairOrder::getTitle, keyword));
        }
        wrapper.orderByDesc(RepairOrder::getSubmitTime);
        return convertPage(orderMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /** 工单详情(含日志) */
    public OrderVO detail(Long orderId) {
        return toVO(getOrder(orderId));
    }

    /** 工单流转日志(时间线) */
    public List<LogVO> logs(Long orderId) {
        List<OrderLog> logs = logMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getOrderId, orderId).orderByAsc(OrderLog::getCreateTime));
        Map<Long, User> users = userMapper.selectBatchIds(
                        logs.stream().map(OrderLog::getOperatorId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return logs.stream().map(l -> {
            LogVO vo = new LogVO();
            vo.setId(l.getId());
            User u = users.get(l.getOperatorId());
            vo.setOperatorName(u == null ? "未知用户" : u.getRealName());
            vo.setAction(l.getAction());
            vo.setActionText(actionText(l.getAction()));
            vo.setRemark(l.getRemark());
            vo.setCreateTime(l.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private String actionText(String action) {
        return switch (action) {
            case OrderLog.ACTION_SUBMIT -> "提交报修";
            case OrderLog.ACTION_ASSIGN -> "管理员派单";
            case OrderLog.ACTION_FINISH -> "维修完成";
            case OrderLog.ACTION_PASS -> "验收通过";
            case OrderLog.ACTION_REJECT -> "验收不通过";
            case OrderLog.ACTION_CANCEL -> "撤销工单";
            case OrderLog.ACTION_CLOSE -> "关闭工单";
            case OrderLog.ACTION_URGE -> "催办";
            default -> action;
        };
    }

    // ==================== 私有工具 ====================

    private RepairOrder getOrder(Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        return order;
    }

    /** 状态校验:当前状态必须是期望状态 */
    private void checkStatus(RepairOrder order, int expected, String message) {
        if (order.getStatus() != expected) {
            throw new BusinessException(message);
        }
    }

    /** 校验当前用户是否是工单的报修人/维修工 */
    private void checkOwner(RepairOrder order, Long userId, String message) {
        if (!order.getUserId().equals(userId) && !order.getWorkerId().equals(userId)) {
            throw new BusinessException(message);
        }
    }

    private void addLog(Long orderId, Long operatorId, String action, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setRemark(remark);
        logMapper.insert(log);
    }

    private void sendMessage(Long receiverId, String content) {
        Message msg = new Message();
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }

    /** 通知某角色的所有用户 */
    private void notifyRole(String role, String content) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, role).eq(User::getStatus, 1));
        users.forEach(u -> sendMessage(u.getId(), content));
    }

    /** 实体分页 -> VO分页(补齐关联名称) */
    private Page<OrderVO> convertPage(Page<RepairOrder> page) {
        Page<OrderVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<OrderVO> vos = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        result.setRecords(vos);
        return result;
    }

    private OrderVO toVO(RepairOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTypeId(order.getTypeId());
        vo.setLocationId(order.getLocationId());
        vo.setTitle(order.getTitle());
        vo.setDescription(order.getDescription());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setWorkerId(order.getWorkerId());
        vo.setPriority(order.getPriority());
        vo.setUrgeCount(order.getUrgeCount());
        vo.setHandleNote(order.getHandleNote());
        vo.setSubmitTime(order.getSubmitTime());
        vo.setAssignTime(order.getAssignTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setAcceptTime(order.getAcceptTime());

        if (StringUtils.hasText(order.getImages())) {
            List<String> imgs = new ArrayList<>();
            for (String s : order.getImages().split(",")) {
                if (StringUtils.hasText(s)) imgs.add(s.trim());
            }
            vo.setImages(imgs);
        }

        // 关联查询名称(逐条查询,数据量小可接受;零基础易理解)
        User submitter = userMapper.selectById(order.getUserId());
        if (submitter != null) {
            vo.setUserName(submitter.getRealName());
            vo.setUserPhone(submitter.getPhone());
        }
        if (order.getWorkerId() != null) {
            User worker = userMapper.selectById(order.getWorkerId());
            vo.setWorkerName(worker == null ? null : worker.getRealName());
        }
        RepairType type = typeMapper.selectById(order.getTypeId());
        if (type != null) {
            vo.setTypeName(type.getTypeName());
        }
        Location loc = locationMapper.selectById(order.getLocationId());
        if (loc != null) {
            vo.setLocationText(loc.getBuilding() + " " + loc.getFloor() + " " + loc.getRoom());
        }
        Evaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, order.getId()).last("LIMIT 1"));
        if (evaluation != null) {
            vo.setScore(evaluation.getScore());
            vo.setEvaluationContent(evaluation.getContent());
        }
        return vo;
    }

    public static String statusText(int status) {
        return switch (status) {
            case 0 -> "待派单";
            case 1 -> "处理中";
            case 2 -> "待验收";
            case 3 -> "已完成";
            case 4 -> "已关闭";
            default -> "未知";
        };
    }

    /** 统计平均处理时长(小时)供超时任务使用 */
    public static long hoursSince(LocalDateTime from) {
        return from == null ? 0 : ChronoUnit.HOURS.between(from, LocalDateTime.now());
    }
}
