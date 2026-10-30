package com.repair.common;

/**
 * 业务异常(可预期的错误,如状态流转不合法)
 *
 * code 用于前端差异化处理:
 *  - 4001 技能不匹配: 人工派单时允许管理员确认后强制派单, 前端据此弹确认框
 *  - 其余默认 500, 前端直接提示 msg
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
