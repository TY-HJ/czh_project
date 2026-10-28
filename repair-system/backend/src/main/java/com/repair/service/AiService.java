package com.repair.service;

import com.repair.common.BusinessException;
import com.repair.config.AiProperties;
import com.repair.dto.AiTextDTO;
import com.repair.entity.RepairOrder;
import com.repair.entity.RepairType;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.RepairTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助填写: 只做"把零散信息组织成一段通顺文字", 不参与任何业务判断。
 *
 * 安全设计:
 *   1. API Key 只存在于服务端(AiProperties), 前端只调用本项目 /api/ai/**, 请求里没有任何密钥;
 *   2. Key 不写日志、不写响应, 出错只回通用提示;
 *   3. 未配置 Key 或调用失败时抛业务异常, 前端提示"手动填写", 不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiProperties props;
    private final RepairOrderMapper orderMapper;
    private final RepairTypeMapper typeMapper;

    /** 报修描述的写作要求 */
    private static final String SYSTEM_DESC = """
            你是社区物业报修系统的填表助手。根据住户给出的故障类型、位置、标题、紧急程度和补充关键词，
            替他写一段 40~80 字的中文报修描述。
            要求：
            1. 只写现象和影响（哪里、什么毛病、持续多久、影响到什么），不写原因判断，不写解决办法；
            2. 不编造未提供的信息（不要写品牌、金额、具体日期等没给出的细节）；
            3. 不用"您好""感谢""请尽快"之类客套话，不加标题、不加引号；
            4. 平实口语，直接输出正文一段话。
            """;

    /** 完工说明的写作要求 */
    private static final String SYSTEM_NOTE = """
            你是维修工单的记录助手。根据工单标题、故障类型、维修耗时和维修工补充的关键词，
            写一段 60~100 字的中文完工说明。
            要求：
            1. 按"到场检查 — 判断原因 — 处理动作 — 测试结果"的顺序组织，像师傅的工单记录；
            2. 只使用给定信息和关键词中出现的事实，不要编造更换的配件型号、费用、住户签字；
            3. 不用客套话，不加标题、不加引号，客观简练，直接输出正文一段话。
            """;

    private volatile RestClient client;

    /** 住户侧: 生成报修描述草稿 */
    public String orderDescription(AiTextDTO dto) {
        String title = safe(dto.getTitle());
        String keywords = safe(dto.getKeywords());
        if (title.isEmpty() && keywords.isEmpty()) {
            throw new BusinessException("请先填写标题或几个关键词,AI 才有内容可依据");
        }
        StringBuilder ctx = new StringBuilder();
        append(ctx, "故障类型", dto.getTypeName());
        append(ctx, "报修位置", dto.getLocation());
        append(ctx, "标题", title);
        append(ctx, "紧急程度", "URGENT".equals(dto.getPriority()) ? "紧急" : "普通");
        append(ctx, "住户补充的关键信息", keywords);
        return chat(SYSTEM_DESC, ctx.toString(), 320);
    }

    /** 维修工侧: 生成完工说明草稿(后端读取工单, 避免前端伪造) */
    public String finishNote(Long orderId, Long userId, String keywords) {
        if (orderId == null) {
            throw new BusinessException("工单信息缺失,请刷新后重试");
        }
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        // 只有该单当前的维修工才能生成, 防止越权读取他人工单内容
        if (order.getWorkerId() == null || !order.getWorkerId().equals(userId)) {
            throw new BusinessException("只有该工单的维修工才能生成处理说明");
        }

        StringBuilder ctx = new StringBuilder();
        append(ctx, "工单标题", order.getTitle());
        append(ctx, "故障类型", typeNameOf(order.getTypeId()));
        append(ctx, "紧急程度", "URGENT".equals(order.getPriority()) ? "紧急" : "普通");
        append(ctx, "维修耗时", durationOf(order));
        append(ctx, "维修工补充的关键信息", safe(keywords));
        return chat(SYSTEM_NOTE, ctx.toString(), 400);
    }

    // ==================== 内部实现 ====================

    private String chat(String systemPrompt, String userContent, int maxTokens) {
        ensureReady();
        Map<String, Object> payload = Map.of(
                "model", props.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)),
                "temperature", 0.6,
                "max_tokens", maxTokens,
                "stream", false);
        try {
            Map<?, ?> resp = client().post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            String text = extractContent(resp);
            if (text == null || text.isBlank()) {
                throw new BusinessException("AI 没有返回内容,请重试或手动填写");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // 上游错误只截断记录, 不把原始报文抛给前端(可能含内部信息)
            log.warn("AI 接口返回异常: status={}, body={}", e.getStatusCode().value(), brief(e.getResponseBodyAsString()));
            throw new BusinessException("AI 服务返回异常(" + e.getStatusCode().value() + "),请稍后重试或手动填写");
        } catch (Exception e) {
            log.warn("AI 调用失败: {}", e.getMessage());
            throw new BusinessException("AI 服务暂时不可用(网络或超时),请手动填写");
        }
    }

    private void ensureReady() {
        if (!props.isEnabled()) {
            throw new BusinessException("AI 辅助功能已关闭,请手动填写");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BusinessException("服务端未配置 AI 密钥,请手动填写(管理员可配置环境变量 AI_API_KEY)");
        }
    }

    private RestClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(5000);
                    factory.setReadTimeout(Math.max(props.getTimeoutSeconds(), 5) * 1000);
                    client = RestClient.builder()
                            .baseUrl(trimEnd(props.getBaseUrl()))
                            .requestFactory(factory)
                            // 密钥只存在于服务端, 由这里注入到出站请求头
                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                            .build();
                }
            }
        }
        return client;
    }

    /** 从 OpenAI 兼容响应里取 choices[0].message.content */
    private String extractContent(Map<?, ?> resp) {
        if (resp == null) {
            return null;
        }
        if (!(resp.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            return null;
        }
        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : content.toString();
    }

    private String typeNameOf(Long typeId) {
        if (typeId == null) {
            return "";
        }
        RepairType type = typeMapper.selectById(typeId);
        return type == null ? "" : type.getTypeName();
    }

    /** 维修耗时: 优先按接单时间算, 未接单则按派单时间算 */
    private String durationOf(RepairOrder order) {
        LocalDateTime start = order.getWorkerAcceptTime() != null
                ? order.getWorkerAcceptTime() : order.getAssignTime();
        if (start == null) {
            return "";
        }
        long minutes = Math.max(Duration.between(start, LocalDateTime.now()).toMinutes(), 1);
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private void append(StringBuilder sb, String label, String value) {
        String v = safe(value);
        if (!v.isEmpty()) {
            sb.append(label).append("：").append(v).append('\n');
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String trimEnd(String url) {
        String u = safe(url);
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
