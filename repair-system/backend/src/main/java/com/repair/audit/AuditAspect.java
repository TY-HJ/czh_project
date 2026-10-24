package com.repair.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repair.entity.OperationLog;
import com.repair.entity.User;
import com.repair.mapper.OperationLogMapper;
import com.repair.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面: 拦截 @AuditLog 注解的方法, 记录操作人/模块/动作/参数(脱敏)/IP/结果
 * 无论成功失败均留痕, 失败时记录失败原因
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final OperationLogMapper logMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        OperationLog record = new OperationLog();
        record.setModule(auditLog.module());
        record.setAction(auditLog.action());

        // 操作人: 未登录(如登录失败)记空
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            record.setUserId(userId);
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));
            record.setUsername(user == null ? null : user.getUsername());
        }

        // 请求信息与来源IP
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            record.setMethod(request.getMethod());
            record.setUri(request.getRequestURI());
            record.setIp(resolveIp(request));
        }

        // 参数摘要: 密码等敏感字段脱敏, 超长截断
        record.setParams(buildParams(pjp));

        try {
            Object result = pjp.proceed();
            record.setSuccess(1);
            return result;
        } catch (Throwable e) {
            record.setSuccess(0);
            record.setErrorMsg(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500));
            throw e;
        } finally {
            try {
                record.setCreateTime(LocalDateTime.now());
                logMapper.insert(record);
            } catch (Exception ex) {
                log.warn("操作日志写入失败: {}", ex.getMessage());
            }
        }
    }

    /** 参数序列化 + 敏感字段脱敏 + 截断 */
    private String buildParams(ProceedingJoinPoint pjp) {
        try {
            Object[] args = pjp.getArgs();
            JsonNode node = objectMapper.valueToTree(args);
            maskSensitive(node);
            String json = objectMapper.writeValueAsString(node);
            return truncate(json, 1000);
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归将字段名含 password 的字段值替换为 *** */
    private void maskSensitive(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.fieldNames().forEachRemaining(name -> {
                JsonNode child = obj.get(name);
                if (name.toLowerCase().contains("password")) {
                    obj.put(name, "***");
                } else if (child != null) {
                    maskSensitive(child);
                }
            });
        } else if (node != null && node.isArray()) {
            node.forEach(this::maskSensitive);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(截断)";
    }

    /** 来源IP: 优先取 X-Forwarded-For 第一段(经过反向代理的场景) */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
