package com.repair.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解: 标注在需要留痕的写操作接口上
 * 由 AuditAspect 切面拦截, 自动写入 operation_log 表
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 业务模块, 如: 工单管理 */
    String module();

    /** 动作描述, 如: 手动派单 */
    String action();
}
