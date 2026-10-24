package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.repair.common.BusinessException;
import com.repair.dto.RegisterDTO;
import com.repair.dto.UserDTO;
import com.repair.entity.RepairOrder;
import com.repair.entity.User;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.UserMapper;
import com.repair.security.JwtUtil;
import com.repair.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 用户与认证服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final RepairOrderMapper orderMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 连续失败多少次触发锁定 */
    private static final int MAX_FAIL_COUNT = 5;
    /** 锁定时长(分钟) */
    private static final int LOCK_MINUTES = 10;

    /**
     * 登录(带锁定策略):
     * 同一账号连续5次密码错误后锁定10分钟; 提示不区分"用户不存在/密码错误"防账号枚举; 成功后失败计数清零
     */
    public LoginVO login(String username, String rawPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            if (user != null) {
                recordLoginFail(user);
            }
            // 统一提示, 不暴露账号是否存在
            throw new BusinessException("用户名或密码错误");
        }
        // 锁定中(密码正确也拦截): 提示剩余等待时间
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            long minutes = (long) Math.ceil(ChronoUnit.SECONDS.between(LocalDateTime.now(), user.getLockUntil()) / 60.0);
            throw new BusinessException("账号已锁定,请" + Math.max(minutes, 1) + "分钟后再试");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用,请联系管理员");
        }
        // 登录成功: 清零失败计数并解除锁定(显式set null, updateById会忽略null字段)
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLoginFailCount, 0)
                .set(User::getLockUntil, null));

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generate(user.getId(), user.getRole()));
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    /** 记录一次登录失败; 连续第5次错误时锁定10分钟并清零计数 */
    private void recordLoginFail(User user) {
        int fails = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId());
        if (fails >= MAX_FAIL_COUNT) {
            uw.set(User::getLoginFailCount, 0)
                    .set(User::getLockUntil, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        } else {
            uw.set(User::getLoginFailCount, fails);
        }
        userMapper.update(null, uw);
    }

    /** 住户自行注册 */
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRole("RESIDENT");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 分页查询用户(管理员),支持按角色/关键字过滤 */
    public Page<User> page(long current, long size, String role, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        page.getRecords().forEach(u -> u.setPassword(null)); // 不回传密码
        return page;
    }

    /** 按角色查询全部用户(如派单时下拉选维修工) */
    public List<User> listByRole(String role) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, role)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId));
    }

    /** 管理员新增用户 */
    public void add(UserDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("请设置初始密码");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRole(dto.getRole());
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 管理员编辑用户 */
    public void update(Long id, UserDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 用户名不能与他人重复
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()).ne(User::getId, id));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRole(dto.getRole());
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(user);
    }

    /** 禁用/启用用户(不允许操作当前登录账号自己) */
    public void toggleStatus(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能禁用当前登录账号");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);
    }

    /** 删除用户:不允许删自己;已有工单关联的用户只能禁用不能删,防止工单/评价数据悬空 */
    public void delete(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        Long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getUserId, id)
                .or()
                .eq(RepairOrder::getWorkerId, id));
        if (orderCount > 0) {
            throw new BusinessException("该用户存在关联工单,不能删除,建议改为禁用");
        }
        userMapper.deleteById(id);
    }
}
