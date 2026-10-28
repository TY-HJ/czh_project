package com.repair.dto;

import lombok.Data;

/**
 * AI 辅助填写的入参(两个场景共用)。
 * 只传业务信息, 不含任何密钥；密钥始终留在服务端。
 */
@Data
public class AiTextDTO {

    /** 故障类型名称, 如「水电维修」 */
    private String typeName;

    /** 报修位置, 如「3栋 2单元 501」 */
    private String location;

    /** 报修标题 */
    private String title;

    /** 紧急程度: NORMAL / URGENT */
    private String priority;

    /** 用户零散输入的补充信息, 作为生成依据 */
    private String keywords;

    /** 完工说明场景: 工单ID(由后端据此校验归属并读取工单信息) */
    private Long orderId;
}
