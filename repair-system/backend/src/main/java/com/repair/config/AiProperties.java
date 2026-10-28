package com.repair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 辅助填写配置(对应 application.yml 中的 ai 节点)。
 *
 * 安全要点: apiKey 只允许来自环境变量 AI_API_KEY 或本地 application-local.yml,
 * 严禁写进会提交到版本库的 application.yml。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 总开关: 关闭后 /api/ai/** 直接提示未启用 */
    private boolean enabled = true;

    /** OpenAI 兼容接口地址, DeepSeek 为 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名 */
    private String model = "deepseek-chat";

    /** 密钥: 仅服务端可见, 不下发给前端 */
    private String apiKey;

    /** 读超时(秒), 需小于前端 axios 的 30 秒, 保证前端能收到后端的兜底提示 */
    private Integer timeoutSeconds = 20;

    /** 单次生成的最大 token 数 */
    private Integer maxTokens = 400;
}
