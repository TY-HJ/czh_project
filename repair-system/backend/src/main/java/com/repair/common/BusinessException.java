package com.repair.common;

/**
 * 业务异常(可预期的错误,如状态流转不合法)
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
