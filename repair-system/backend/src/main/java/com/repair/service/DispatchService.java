package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.repair.common.BusinessException;
import com.repair.entity.*;
import com.repair.mapper.*;
import com.repair.vo.CandidateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能派单子系统(V2.0 核心)
 *
 * 流程: 提交工单 -> 读规则 -> 判定链(总开关/技能在岗/未满负荷)
 *      -> 任一不满足转人工(写 HOLD 时间轴并通知管理员)
 *      -> 负荷/历史评分/楼栋熟悉度三维归一化打分(紧急工单放大负荷权重)
 *      -> 得分排序取Top1 -> 带状态条件乐观更新工单(防并发重复派单)
 *      -> TopN得分明细JSON存档 -> 写流转日志与站内消息
 *
 * 自动改派: 处理中超 reassign_hours 未完成 -> 排除 tried_workers 后改派次优
 *          -> 改派次数+1(上限 reassign_limit) -> 超限转人工
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final RepairOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final WorkerSkillMapper workerSkillMapper;
    private final DispatchRuleMapper ruleMapper;
    private final EvaluationMapper evaluationMapper;
    private final LocationMapper locationMapper;
    private final RepairTypeMapper typeMapper;
    private final OrderLogMapper logMapper;
    private final MessageMapper messageMapper;

    /** 派单方式 */
    public static final String TYPE_AUTO = "AUTO";
    public static final String TYPE_MANUAL = "MANUAL";

    // ==================== 规则 ====================

    /** 读取派单规则(表中无记录时返回默认值, 保证系统可运行) */
    public DispatchRule getRule() {
        DispatchRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DispatchRule>().last("LIMIT 1"));
        if (rule != null) {
            return rule;
        }
        DispatchRule def = new DispatchRule();
        def.setAutoDispatchEnabled(true);
        def.setWLoad(new BigDecimal("0.40"));
        def.setWRating(new BigDecimal("0.35"));
        def.setWFamiliarity(new BigDecimal("0.25"));
        def.setUrgencyBoost(new BigDecimal("0.50"));
        def.setMaxLoad(5);
        def.setFamiliarityFull(5);
        def.setDefaultRating(new BigDecimal("0.60"));
        def.setReassignLimit(2);
        def.setReassignHours(48);
        return def;
    }

    /** 保存规则: 校验各权重∈[0,1]且总和>0 */
    public void saveRule(DispatchRule dto) {
        DispatchRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<DispatchRule>().last("LIMIT 1"));
        if (rule == null) {
            rule = new DispatchRule();
        }
        BigDecimal sum = dto.getWLoad().add(dto.getWRating()).add(dto.getWFamiliarity());
        if (outOfRange(dto.getWLoad()) || outOfRange(dto.getWRating()) || outOfRange(dto.getWFamiliarity())) {
            throw new BusinessException("各权重必须在 0~1 之间");
        }
        if (sum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("权重之和必须大于0");
        }
        if (dto.getUrgencyBoost().compareTo(BigDecimal.ZERO) < 0
                || dto.getUrgencyBoost().compareTo(new BigDecimal("2")) > 0) {
            throw new BusinessException("紧急放大系数必须在 0~2 之间");
        }
        if (dto.getMaxLoad() == null || dto.getMaxLoad() < 1) {
            throw new BusinessException("负荷上限必须为正整数");
        }
        if (dto.getFamiliarityFull() == null || dto.getFamiliarityFull() < 1) {
            throw new BusinessException("熟悉度满分单数必须为正整数");
        }
        if (dto.getReassignLimit() == null || dto.getReassignLimit() < 0) {
            throw new BusinessException("改派次数上限不能为负数");
        }
        if (dto.getReassignHours() == null || dto.getReassignHours() < 1) {
            throw new BusinessException("改派超时阈值必须为正整数小时");
        }
        rule.setAutoDispatchEnabled(Boolean.TRUE.equals(dto.getAutoDispatchEnabled()));
        rule.setWLoad(dto.getWLoad());
        rule.setWRating(dto.getWRating());
        rule.setWFamiliarity(dto.getWFamiliarity());
        rule.setUrgencyBoost(dto.getUrgencyBoost());
        rule.setMaxLoad(dto.getMaxLoad());
        rule.setFamiliarityFull(dto.getFamiliarityFull());
        rule.setDefaultRating(dto.getDefaultRating() == null ? new BigDecimal("0.60") : dto.getDefaultRating());
        rule.setReassignLimit(dto.getReassignLimit());
        rule.setReassignHours(dto.getReassignHours());
        if (rule.getId() == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
    }

    private boolean outOfRange(BigDecimal w) {
        return w == null || w.compareTo(BigDecimal.ZERO) < 0 || w.compareTo(BigDecimal.ONE) > 0;
    }

    // ==================== 候选人打分 ====================

    /**
     * 工单候选人实时得分列表(已按综合得分降序)
     * 硬约束: 1) 技能标签包含工单报修类型 2) 账号启用
     */
    public List<CandidateVO> liveCandidates(RepairOrder order, DispatchRule rule) {
        // 技能匹配的维修工ID集合
        Set<Long> skilledIds = workerSkillMapper.selectList(new LambdaQueryWrapper<WorkerSkill>()
                        .eq(WorkerSkill::getTypeId, order.getTypeId()))
                .stream().map(WorkerSkill::getWorkerId).collect(Collectors.toSet());
        if (skilledIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 在岗维修工
        List<User> workers = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "WORKER").eq(User::getStatus, 1))
                .stream().filter(w -> skilledIds.contains(w.getId())).toList();
        if (workers.isEmpty()) {
            return new ArrayList<>();
        }

        String building = buildingOf(order.getLocationId());
        boolean urgent = "URGENT".equals(order.getPriority());
        BigDecimal wLoad = rule.getWLoad();
        if (urgent) {
            // 紧急工单负荷权重放大
            wLoad = wLoad.multiply(BigDecimal.ONE.add(rule.getUrgencyBoost()));
        }

        List<CandidateVO> result = new ArrayList<>();
        for (User w : workers) {
            CandidateVO vo = scoreCandidate(w, building, rule, wLoad);
            result.add(vo);
        }
        result.sort(Comparator.comparing(CandidateVO::getTotalScore).reversed());
        return result;
    }

    /** 单个维修工三维打分 */
    private CandidateVO scoreCandidate(User worker, String building, DispatchRule rule, BigDecimal wLoad) {
        CandidateVO vo = new CandidateVO();
        vo.setWorkerId(worker.getId());
        vo.setWorkerName(worker.getRealName());
        vo.setPhone(worker.getPhone());

        // 1) 负荷分: 当前未完成工单数(处理中/待验收)
        long load = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getWorkerId, worker.getId())
                .in(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING, RepairOrder.STATUS_PENDING_ACCEPTANCE));
        vo.setCurrentLoad((int) load);
        double loadScore = 1 - Math.min((double) load / rule.getMaxLoad(), 1);
        vo.setLoadScore(round2(loadScore));

        // 2) 历史评分分: 全部验收评价平均分(1~5), 无评价取 default_rating
        List<Integer> scores = evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                        .eq(Evaluation::getWorkerId, worker.getId()).select(Evaluation::getScore))
                .stream().map(Evaluation::getScore).toList();
        double ratingScore;
        if (scores.isEmpty()) {
            vo.setAvgRating(null);
            ratingScore = rule.getDefaultRating().doubleValue();
        } else {
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            vo.setAvgRating(round2(avg));
            ratingScore = avg / 5;
        }
        vo.setRatingScore(round2(ratingScore));

        // 3) 楼栋熟悉度分: 该维修工在工单所在楼栋的历史已完成工单数
        //    注意数的是"工单数"而不是"去过几个房间": 同一房间修过多次也累加, 才符合熟悉度语义
        int familiar = 0;
        if (building != null) {
            List<Long> sameBuildingLocIds = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                            .eq(Location::getBuilding, building)
                            .select(Location::getId))
                    .stream().map(Location::getId).toList();
            if (!sameBuildingLocIds.isEmpty()) {
                Long cnt = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getWorkerId, worker.getId())
                        .eq(RepairOrder::getStatus, RepairOrder.STATUS_COMPLETED)
                        .in(RepairOrder::getLocationId, sameBuildingLocIds));
                familiar = cnt == null ? 0 : cnt.intValue();
            }
        }
        vo.setFamiliarCount(familiar);
        double famScore = Math.min((double) familiar / rule.getFamiliarityFull(), 1);
        vo.setFamScore(round2(famScore));

        // 综合得分
        double total = wLoad.doubleValue() * loadScore
                + rule.getWRating().doubleValue() * ratingScore
                + rule.getWFamiliarity().doubleValue() * famScore;
        vo.setTotalScore(round2(total));
        return vo;
    }

    private String buildingOf(Long locationId) {
        if (locationId == null) {
            return null;
        }
        Location loc = locationMapper.selectById(locationId);
        return loc == null ? null : loc.getBuilding();
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ==================== 自动派单 ====================

    /**
     * 工单提交后自动派单(乐观更新防并发), 由 OrderService.create 在同一事务内调用。
     *
     * 判定链(任一不满足即转人工, 并写时间轴 + 通知管理员):
     *   1) 自动派单总开关打开
     *   2) 存在技能匹配且在岗的维修工
     *   3) 其中至少有一人未满负荷(未完成工单数 &lt; max_load)
     *
     * @return null 表示已自动派出; 非 null 为转人工的原因
     */
    @Transactional
    public String autoDispatch(Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != RepairOrder.STATUS_PENDING_DISPATCH) {
            return null;
        }
        DispatchRule rule = getRule();

        // 条件1: 全局开关
        if (!Boolean.TRUE.equals(rule.getAutoDispatchEnabled())) {
            return hold(order, "自动派单总开关已关闭");
        }
        // 条件2: 技能匹配 + 在岗
        List<CandidateVO> candidates = liveCandidates(order, rule);
        if (candidates.isEmpty()) {
            RepairType type = order.getTypeId() == null ? null : typeMapper.selectById(order.getTypeId());
            String typeName = type == null ? "" : type.getTypeName();
            return hold(order, "暂无技能匹配的在岗维修工" + (typeName.isEmpty() ? "" : "(" + typeName + ")"));
        }
        // 条件3: 满负荷的师傅不参与自动派单, 避免把单压给已经忙不过来的师傅
        int maxLoad = rule.getMaxLoad();
        List<CandidateVO> available = candidates.stream()
                .filter(c -> c.getCurrentLoad() == null || c.getCurrentLoad() < maxLoad)
                .collect(Collectors.toList());
        if (available.isEmpty()) {
            return hold(order, "技能匹配的维修工均已满负荷(上限" + maxLoad + "单)");
        }

        CandidateVO top = available.get(0);

        // 带状态条件的乐观更新: 仅当工单仍处于"待派单"才更新, 防并发重复派单
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PENDING_DISPATCH)
                .set(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .set(RepairOrder::getWorkerId, top.getWorkerId())
                .set(RepairOrder::getAssignTime, LocalDateTime.now())
                .set(RepairOrder::getDispatchType, TYPE_AUTO)
                .set(RepairOrder::getDispatchScore, BigDecimal.valueOf(top.getTotalScore()))
                .set(RepairOrder::getReassignCount, 0)
                .set(RepairOrder::getTriedWorkers, String.valueOf(top.getWorkerId())));
        if (rows == 0) {
            // 条件更新未命中: 工单已被其他线程处理
            log.info("工单{}自动派单乐观更新未命中,放弃本轮", order.getOrderNo());
            return null;
        }

        addLog(orderId, OrderLog.ACTION_AUTO_ASSIGN,
                "智能派单给 " + top.getWorkerName() + " (综合得分" + top.getTotalScore() + ")");
        sendMessage(top.getWorkerId(), "您有新的维修任务【" + order.getOrderNo() + "】" + order.getTitle()
                + "(系统智能派单,得分" + top.getTotalScore() + "),请及时接单处理");
        log.info("工单{}智能派单给{} 得分{}", order.getOrderNo(), top.getWorkerName(), top.getTotalScore());
        return null;
    }

    /** 自动派单判定未通过: 写工单时间轴留痕 + 通知管理员, 返回转人工原因 */
    private String hold(RepairOrder order, String reason) {
        addLog(order.getId(), OrderLog.ACTION_HOLD, "转人工派单:" + reason);
        notifyAdmins("工单【" + order.getOrderNo() + "】未自动派单(" + reason + "),请人工派单");
        log.info("工单{}转人工派单: {}", order.getOrderNo(), reason);
        return reason;
    }

    // ==================== 自动改派 ====================

    /**
     * 处中超 reassign_hours 未完成的工单自动改派
     * 三重约束: 刷新派单时间 + tried_workers 排除 + 改派次数上限
     * 由超时定时任务调用; 改派成功返回 true
     */
    @Transactional
    public boolean autoReassign(RepairOrder order) {
        DispatchRule rule = getRule();
        // 超过改派上限: 转人工
        if (order.getReassignCount() != null && order.getReassignCount() >= rule.getReassignLimit()) {
            notifyAdmins("工单【" + order.getOrderNo() + "】已自动改派" + order.getReassignCount()
                    + "次仍超时未完成,请人工介入");
            return false;
        }
        // 排除已尝试过的维修工
        Set<Long> tried = parseTried(order.getTriedWorkers());
        List<CandidateVO> candidates = liveCandidates(order, rule).stream()
                .filter(c -> !tried.contains(c.getWorkerId()))
                .filter(c -> c.getCurrentLoad() == null || c.getCurrentLoad() < rule.getMaxLoad())
                .toList();
        if (candidates.isEmpty()) {
            notifyAdmins("工单【" + order.getOrderNo() + "】超时且无可用候选维修工(已尝试"
                    + (tried.isEmpty() ? "无" : tried.size() + "人") + "),请人工派单");
            return false;
        }
        CandidateVO next = candidates.get(0);
        Long oldWorkerId = order.getWorkerId();

        // 追加 tried_workers
        tried.add(next.getWorkerId());
        String triedStr = tried.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 乐观更新: 仅当工单仍由原维修工持有且处于处理中才改派
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, order.getId())
                .eq(RepairOrder::getStatus, RepairOrder.STATUS_PROCESSING)
                .eq(oldWorkerId != null, RepairOrder::getWorkerId, oldWorkerId)
                .set(RepairOrder::getWorkerId, next.getWorkerId())
                .set(RepairOrder::getAssignTime, LocalDateTime.now())   // 刷新派单时间
                .set(RepairOrder::getWorkerAcceptTime, null)            // 新维修工需重新接单
                .set(RepairOrder::getDispatchScore, BigDecimal.valueOf(next.getTotalScore()))
                .set(RepairOrder::getReassignCount, (order.getReassignCount() == null ? 0 : order.getReassignCount()) + 1)
                .set(RepairOrder::getTriedWorkers, triedStr));
        if (rows == 0) {
            return false;
        }
        int times = (order.getReassignCount() == null ? 0 : order.getReassignCount()) + 1;
        addLog(order.getId(), OrderLog.ACTION_REASSIGN,
                "处理超" + rule.getReassignHours() + "小时自动改派给 " + next.getWorkerName()
                        + " (第" + times + "次,得分" + next.getTotalScore() + ")");
        if (oldWorkerId != null) {
            sendMessage(oldWorkerId, "工单【" + order.getOrderNo() + "】因超时未完成已自动改派给其他维修工");
        }
        sendMessage(next.getWorkerId(), "工单【" + order.getOrderNo() + "】已自动改派给您(得分"
                + next.getTotalScore() + "),请及时接单处理");
        sendMessage(order.getUserId(), "您的报修【" + order.getOrderNo() + "】因处理超时已自动改派维修工,请耐心等待");
        log.info("工单{}自动改派给{} (第{}次)", order.getOrderNo(), next.getWorkerName(), times);
        return true;
    }

    private Set<Long> parseTried(String triedWorkers) {
        Set<Long> set = new LinkedHashSet<>();
        if (triedWorkers != null && !triedWorkers.isBlank()) {
            for (String s : triedWorkers.split(",")) {
                try {
                    set.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignored) { }
            }
        }
        return set;
    }

    // ==================== 管理员派单弹窗数据源 ====================

    /**
     * 派单弹窗候选列表: 所有在岗维修工 + 技能是否匹配 + 实时三维得分。
     *
     * 与 liveCandidates 的区别: 这里不把"缺技能""已满负荷"的人过滤掉,
     * 而是打上标记交给管理员判断 —— 否则某类型只有一人会修、他又休假时,
     * 弹窗会是空的, 工单永远派不出去。
     */
    public List<Map<String, Object>> assignCandidates(Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        DispatchRule rule = getRule();
        Set<Long> skilledIds = workerSkillMapper.selectList(new LambdaQueryWrapper<WorkerSkill>()
                        .eq(WorkerSkill::getTypeId, order.getTypeId()))
                .stream().map(WorkerSkill::getWorkerId).collect(Collectors.toSet());

        String building = buildingOf(order.getLocationId());
        BigDecimal wLoad = rule.getWLoad();
        if ("URGENT".equals(order.getPriority())) {
            wLoad = wLoad.multiply(BigDecimal.ONE.add(rule.getUrgencyBoost()));
        }

        List<User> workers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "WORKER").eq(User::getStatus, 1));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User w : workers) {
            CandidateVO vo = scoreCandidate(w, building, rule, wLoad);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("workerId", vo.getWorkerId());
            m.put("workerName", vo.getWorkerName());
            m.put("phone", vo.getPhone());
            m.put("currentLoad", vo.getCurrentLoad());
            m.put("avgRating", vo.getAvgRating());
            m.put("familiarCount", vo.getFamiliarCount());
            m.put("loadScore", vo.getLoadScore());
            m.put("ratingScore", vo.getRatingScore());
            m.put("famScore", vo.getFamScore());
            m.put("totalScore", vo.getTotalScore());
            m.put("skillMatched", skilledIds.contains(w.getId()));
            m.put("overload", vo.getCurrentLoad() != null && vo.getCurrentLoad() >= rule.getMaxLoad());
            m.put("currentWorker", w.getId().equals(order.getWorkerId()));
            result.add(m);
        }

        // 排序: 技能匹配优先 -> 当前指派人(改派时)靠后, 避免误选原人 -> 综合得分从高到低
        result.sort(Comparator
                .comparingInt((Map<String, Object> m) -> Boolean.TRUE.equals(m.get("skillMatched")) ? 0 : 1)
                .thenComparingInt(m -> Boolean.TRUE.equals(m.get("currentWorker")) ? 1 : 0)
                .thenComparingDouble(m -> -((Number) m.get("totalScore")).doubleValue()));
        return result;
    }

    // ==================== 私有工具 ====================

    private void addLog(Long orderId, String action, String remark) {
        OrderLog logEntry = new OrderLog();
        logEntry.setOrderId(orderId);
        logEntry.setOperatorId(0L); // 0 = 系统
        logEntry.setAction(action);
        logEntry.setRemark(remark);
        logMapper.insert(logEntry);
    }

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
        admins.forEach(a -> sendMessage(a.getId(), content));
    }
}
