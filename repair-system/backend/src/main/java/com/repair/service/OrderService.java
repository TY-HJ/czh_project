package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
 *        |--住户撤销/管理员关闭--> [4 已关闭]        [2] --验收通过--> [3 已完成]
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
    private final WorkerSkillMapper workerSkillMapper;
    private final DispatchService dispatchService;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 住户:提交报修 ====================
    @Transactional
    public OrderVO create(Long userId, OrderCreateDTO dto) {
        RepairOrder order = new RepairOrder();
        order.setUserId(userId);
        order.setTypeId(dto.getTypeId());
        order.setLocationId(dto.getLocationId());
        order.setTitle(dto.getTitle());
        order.setDescription(dto.getDescription());
        order.setImages(dto.getImages());
        order.setStatus(RepairOrder.STATUS_PENDING_DISPATCH);
        order.setPriority(StringUtils.hasText(dto.getPriority()) ? dto.getPriority() : "NORMAL");
        order.setUrgeCount(0);
        order.setReassignCount(0);
        insertWithOrderNo(order);   // 工单号并发防重

        addLog(order.getId(), userId, OrderLog.ACTION_SUBMIT, "提交报修");
        // 提交成功后立即触发智能派单: 按规则判定, 通过则自动派出, 不通过则写 HOLD 留痕并通知管理员
        String holdReason = dispatchService.autoDispatch(order.getId());
        // 返回提交结果(含自动派单去向/转人工原因), 前端据此给出准确反馈
        OrderVO vo = toVO(orderMapper.selectById(order.getId()));
        vo.setDispatchHint(holdReason);
        return vo;
    }

    /** 生成工单号并插入; 撞唯一约束(并发提交)时最多重试3次 */
    private void insertWithOrderNo(RepairOrder order) {
        for (int attempt = 0; attempt < 3; attempt++) {
            order.setId(null);
            order.setOrderNo(nextOrderNo());
            try {
                orderMapper.insert(order);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发重号: 重新取号再试
            }
        }
        throw new BusinessException("工单号生成繁忙,请重试");
    }

    /** 工单号规则: yyyyMMdd + 当日4位流水号(取当日最大号+1, 而非计数, 避免删除数据后重号) */
    private String nextOrderNo() {
        String today = LocalDate.now().format(NO_FMT);
        RepairOrder latest = orderMapper.selectOne(new LambdaQueryWrapper<RepairOrder>()
                .likeRight(RepairOrder::getOrderNo, today)
                .orderByDesc(RepairOrder::getOrderNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (latest != null) {
            seq = Integer.parseInt(latest.getOrderNo().substring(today.length())) + 1;
        }
        return today + String.format("%04d", seq);
    }

    // ==================== 管理员:派单/改派(记 MANUAL) ====================
    @Transactional
    public void assign(Long orderId, Long adminId, Long workerId, Boolean force) {
        RepairOrder order = getOrder(orderId);
        // 待派单 -> 派单; 处理中 -> 改派(换人换师傅, 超时重新计时)
        boolean reassign = order.getStatus() == RepairOrder.STATUS_PROCESSING;
        if (order.getStatus() != RepairOrder.STATUS_PENDING_DISPATCH && !reassign) {
            throw new BusinessException("该工单当前状态不可派单");
        }
        if (workerId == null) {
            throw new BusinessException("请选择维修工");
        }
        User worker = userMapper.selectById(workerId);
        if (worker == null || !"WORKER".equals(worker.getRole())) {
            throw new BusinessException("维修工不存在");
        }
        // 在岗校验: 禁用账号登录不了, 派给他等于把单丢进黑洞
        if (worker.getStatus() == null || worker.getStatus() != 1) {
            throw new BusinessException("该维修工已被禁用,无法派单,请先在用户管理中启用");
        }
        // 技能校验: 与自动派单同一口径, 避免把单派给不处理该类故障的师傅
        // 但人工派单保留"强制派出"的口子: 某类型只有一人会修、他又休假/被禁用时,
        // 若一律拦死会导致该类型工单永久卡在待派单, 所以确认后允许放行并留痕
        String forceNote = "";
        Long skillCount = workerSkillMapper.selectCount(new LambdaQueryWrapper<WorkerSkill>()
                .eq(WorkerSkill::getWorkerId, workerId)
                .eq(WorkerSkill::getTypeId, order.getTypeId()));
        if (skillCount == null || skillCount == 0) {
            RepairType type = typeMapper.selectById(order.getTypeId());
            String typeName = type == null ? "该类型" : "「" + type.getTypeName() + "」";
            if (!Boolean.TRUE.equals(force)) {
                throw new BusinessException(4001,
                        "该师傅未配置" + typeName + "技能,仍要派给他吗?");
            }
            forceNote = "(强制派单:该师傅未配置" + typeName + "技能)";
        }
        Long oldWorkerId = order.getWorkerId();
        if (reassign && workerId.equals(oldWorkerId)) {
            throw new BusinessException("该工单当前就派给" + worker.getRealName() + ",请选择其他维修工");
        }
        int oldReassign = order.getReassignCount() == null ? 0 : order.getReassignCount();

        // 带状态条件的乐观更新, 防并发重复派单/改派
        LambdaUpdateWrapper<RepairOrder> update = new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .in(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_DISPATCH, RepairOrder.STATUS_PROCESSING)
                .set(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .set(RepairOrder::getWorkerId, workerId)
                .set(RepairOrder::getAdminId, adminId)
                .set(RepairOrder::getAssignTime, LocalDateTime.now())
                .set(RepairOrder::getWorkerAcceptTime, null)   // 换人后需新师傅重新接单
                .set(RepairOrder::getDispatchType, DispatchService.TYPE_MANUAL)
                .set(RepairOrder::getDispatchScore, null)
                .set(RepairOrder::getReassignCount, reassign ? oldReassign + 1 : 0)
                .set(RepairOrder::getTriedWorkers, mergeTried(order.getTriedWorkers(), workerId));
        if (reassign && oldWorkerId != null) {
            update.eq(RepairOrder::getWorkerId, oldWorkerId);   // 防止并发改派覆盖
        }
        int rows = orderMapper.update(null, update);
        if (rows == 0) {
            throw new BusinessException("该工单已被处理,请刷新后查看");
        }

        if (reassign) {
            String oldName = oldWorkerId == null ? "原维修工" : realNameOf(oldWorkerId);
            addLog(orderId, adminId, OrderLog.ACTION_REASSIGN,
                    "管理员改派: " + oldName + " → " + worker.getRealName() + forceNote);
            if (oldWorkerId != null && !oldWorkerId.equals(workerId)) {
                sendMessage(oldWorkerId, "工单【" + order.getOrderNo() + "】已被管理员改派给其他师傅,您无需再处理");
            }
            sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】已改派给" + worker.getRealName() + ",请等待接单");
        } else {
            addLog(orderId, adminId, OrderLog.ACTION_ASSIGN, "派单给 " + worker.getRealName() + forceNote);
        }
        sendMessage(workerId, "您有新的维修任务【" + order.getOrderNo() + "】" + order.getTitle() + ",请及时接单处理");
    }

    // ==================== 维修工:接单确认 ====================
    @Transactional
    public void accept(Long orderId, Long workerId) {
        RepairOrder order = getOrder(orderId);
        checkOwner(order, workerId, "只有该工单的维修工才能接单");
        checkStatus(order, RepairOrder.STATUS_PROCESSING, "工单当前状态不可接单");
        if (order.getWorkerAcceptTime() != null) {
            throw new BusinessException("您已接单,请及时维修");
        }
        order.setWorkerAcceptTime(LocalDateTime.now());
        orderMapper.updateById(order);

        addLog(orderId, workerId, OrderLog.ACTION_ACCEPT, "维修工已接单,维修中");
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】师傅已接单,维修中");
    }

    // ==================== 维修工:拒单(退回待派单, 由管理员重新派) ====================
    @Transactional
    public void refuse(Long orderId, Long workerId, String reason) {
        RepairOrder order = getOrder(orderId);
        if (!workerId.equals(order.getWorkerId())) {
            throw new BusinessException("只有该工单的维修工才能拒单");
        }
        checkStatus(order, RepairOrder.STATUS_PROCESSING, "工单当前状态不可拒单");
        if (order.getWorkerAcceptTime() != null) {
            throw new BusinessException("您已接单维修,如需转交请联系管理员改派");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写拒单原因");
        }
        String trimmed = reason.trim();
        String workerName = realNameOf(workerId);

        // 带条件更新: 防止并发接单/改派后仍被拒单
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .eq(RepairOrder::getWorkerId, workerId)
                .isNull(RepairOrder::getWorkerAcceptTime)
                .set(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_DISPATCH)
                .set(RepairOrder::getWorkerId, null)
                .set(RepairOrder::getAssignTime, null)
                .set(RepairOrder::getDispatchScore, null)
                .set(RepairOrder::getTriedWorkers, mergeTried(order.getTriedWorkers(), workerId)));
        if (rows == 0) {
            throw new BusinessException("该工单状态已变化,请刷新后查看");
        }

        addLog(orderId, workerId, OrderLog.ACTION_REFUSE, "拒单:" + trimmed);
        notifyRole("ADMIN", "工单【" + order.getOrderNo() + "】被维修工" + workerName + "拒单,原因:" + trimmed
                + ",已退回待派单,请重新派单");
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】正在重新安排维修师傅,请稍候");
    }

    // ==================== 维修工:完成维修 ====================
    @Transactional
    public void finish(Long orderId, Long workerId, String handleNote) {
        RepairOrder order = getOrder(orderId);
        checkOwner(order, workerId, "只有该工单的维修工才能操作");
        checkStatus(order, RepairOrder.STATUS_PROCESSING, "工单当前状态不可标记完成");
        if (order.getWorkerAcceptTime() == null) {
            throw new BusinessException("请先接单再报完工");
        }
        order.setStatus(RepairOrder.STATUS_PENDING_ACCEPTANCE);
        order.setHandleNote(handleNote);
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);

        addLog(orderId, workerId, OrderLog.ACTION_FINISH, handleNote);
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】已维修完成,请及时验收");
    }

    // ==================== 住户:验收(通过/不通过) ====================
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
            // 注意: MyBatis-Plus 的 updateById 默认忽略 null 字段,清空完成时间必须用 UpdateWrapper 显式 set null
            // 同时刷新派单时间: 否则原派单时间早已超过超时阈值, 定时任务会把刚退回的返工单当成超时单改派给他人
            orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, orderId)
                    .set(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                    .set(RepairOrder::getFinishTime, null)
                    .set(RepairOrder::getAssignTime, LocalDateTime.now()));

            addLog(orderId, userId, OrderLog.ACTION_REJECT,
                    "验收不通过:" + (StringUtils.hasText(dto.getContent()) ? dto.getContent() : "未说明原因"));
            if (order.getWorkerId() != null) {
                sendMessage(order.getWorkerId(), "工单【" + order.getOrderNo() + "】验收不通过,请重新处理");
            }
        }
    }

    // ==================== 住户:撤销(仅待派单可撤) ====================
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
    public void urge(Long orderId, Long userId, boolean isAdmin) {
        RepairOrder order = getOrder(orderId);
        if (order.getStatus() != RepairOrder.STATUS_PENDING_DISPATCH
                && order.getStatus() != RepairOrder.STATUS_PROCESSING) {
            throw new BusinessException("工单当前状态无需催办");
        }
        // 归属校验:仅报修人或管理员可催办
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new BusinessException("只有报修人或管理员才能催办");
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

    /** 住户的工单列表 */
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
        // 提交时间正序: 最早的单排最前, 避免老工单一直被新单压在后面无人处理
        wrapper.orderByAsc(RepairOrder::getSubmitTime);
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
        // 状态升序(待派单->处理中->待验收->已完成->已关闭) + 提交时间正序:
        // 待处理的排最前、最老的待处理单排最最前, 管理员先看到最需要处理的
        wrapper.orderByAsc(RepairOrder::getStatus)
               .orderByAsc(RepairOrder::getSubmitTime);
        return convertPage(orderMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 工单详情(含归属校验):
     * 管理员可看全部; 住户只能看自己的; 维修工只能看派给自己的
     */
    public OrderVO detail(Long orderId, Long userId, String role) {
        RepairOrder order = getOrder(orderId);
        checkViewPermission(order, userId, role);
        return toVO(order);
    }

    /** 工单流转日志(时间线, 同详情的归属校验) */
    public List<LogVO> logs(Long orderId, Long userId, String role) {
        RepairOrder order = getOrder(orderId);
        checkViewPermission(order, userId, role);
        List<OrderLog> logs = logMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getOrderId, orderId).orderByAsc(OrderLog::getCreateTime));
        List<Long> operatorIds = logs.stream().map(OrderLog::getOperatorId)
                .filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        Map<Long, User> users = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        return logs.stream().map(l -> {
            LogVO vo = new LogVO();
            vo.setId(l.getId());
            // operatorId=0 表示系统动作(智能派单/自动改派)
            User u = users.get(l.getOperatorId());
            vo.setOperatorName(u == null ? "系统" : u.getRealName());
            vo.setAction(l.getAction());
            vo.setActionText(actionText(l.getAction()));
            vo.setRemark(l.getRemark());
            vo.setCreateTime(l.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查看权限: 管理员全部; 报修人本人; 本单经手过的维修工。
     * 经手人包含"当前指派的"和"改派前/已拒单的"两类:
     * 后者在消息中心仍持有该工单的通知, 若禁止查看会导致消息里的详情入口全部失效。
     */
    private void checkViewPermission(RepairOrder order, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        boolean isOwner = order.getUserId().equals(userId);
        boolean isWorker = order.getWorkerId() != null && order.getWorkerId().equals(userId);
        if (!isOwner && !isWorker && "WORKER".equals(role) && isTriedWorker(order.getTriedWorkers(), userId)) {
            isWorker = true;
        }
        if (!isOwner && !isWorker) {
            throw new BusinessException("无权查看该工单");
        }
    }

    /** triedWorkers 为逗号分隔的维修工ID串, 判断该维修工是否经手过本单 */
    private boolean isTriedWorker(String triedWorkers, Long userId) {
        if (!StringUtils.hasText(triedWorkers)) {
            return false;
        }
        String target = String.valueOf(userId);
        for (String id : triedWorkers.split(",")) {
            if (target.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }

    private String actionText(String action) {
        return switch (action) {
            case OrderLog.ACTION_SUBMIT -> "提交报修";
            case OrderLog.ACTION_ASSIGN -> "管理员派单";
            case OrderLog.ACTION_AUTO_ASSIGN -> "智能派单";
            case OrderLog.ACTION_HOLD -> "转人工派单";
            case OrderLog.ACTION_REASSIGN -> "改派";
            case OrderLog.ACTION_ACCEPT -> "接单确认";
            case OrderLog.ACTION_REFUSE -> "维修工拒单";
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

    /** 校验当前用户是否是工单的报修人/维修工(空值安全: 待派单工单的 workerId 为 null) */
    private void checkOwner(RepairOrder order, Long userId, String message) {
        boolean isOwner = userId.equals(order.getUserId());
        boolean isWorker = userId.equals(order.getWorkerId());
        if (!isOwner && !isWorker) {
            throw new BusinessException(message);
        }
    }

    /** 追加"已尝试过的维修工", 供自动改派避让重复派给同一个人 */
    private String mergeTried(String tried, Long workerId) {
        if (!StringUtils.hasText(tried)) {
            return String.valueOf(workerId);
        }
        List<String> ids = new ArrayList<>(List.of(tried.split(",")));
        String target = String.valueOf(workerId);
        if (!ids.contains(target)) {
            ids.add(target);
        }
        return String.join(",", ids);
    }

    /** 取用户姓名, 找不到时退回"用户#id" */
    private String realNameOf(Long userId) {
        if (userId == null) {
            return "未知用户";
        }
        User user = userMapper.selectById(userId);
        return user == null ? ("用户#" + userId) : user.getRealName();
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
        vo.setDispatchType(order.getDispatchType());
        vo.setDispatchScore(order.getDispatchScore());
        vo.setReassignCount(order.getReassignCount());
        vo.setWorkerAcceptTime(order.getWorkerAcceptTime());
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
            vo.setLocationText(loc.getBuilding() + " " + loc.getUnit() + " " + loc.getRoom());
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
