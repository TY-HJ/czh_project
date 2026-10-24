package com.repair.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息VO:在实体基础上补充关联工单ID,
 * 前端点击消息可直接打开对应工单详情
 */
@Data
public class MessageVO {
    private Long id;
    private String content;
    private Integer isRead;      // 0未读 1已读
    private LocalDateTime createTime;
    private Long orderId;        // 关联工单(从消息文案中的工单号解析, 可为空)
    private String orderNo;
}
