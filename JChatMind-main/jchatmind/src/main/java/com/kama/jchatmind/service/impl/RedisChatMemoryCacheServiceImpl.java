package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.service.ChatMemoryCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class RedisChatMemoryCacheServiceImpl implements ChatMemoryCacheService {

    private static final String KEY_PREFIX = "jchatmind:chat:memory:";
    private static final String SUMMARY_PREFIX = "【历史摘要】";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClientRegistry chatClientRegistry;

    private final int ttlDays;
    private final int maxMessagesPerSession;
    private final int summaryTriggerMessages;
    private final int recentWindowSize;
    private final int summaryMaxChars;
    private final boolean llmSummaryEnabled;
    private final String llmSummaryModel;

    public RedisChatMemoryCacheServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ChatClientRegistry chatClientRegistry,
            @Value("${jchatmind.memory.redis.ttl-days:7}") int ttlDays,
            @Value("${jchatmind.memory.redis.max-messages:200}") int maxMessagesPerSession,
            @Value("${jchatmind.memory.redis.summary-trigger-messages:60}") int summaryTriggerMessages,
            @Value("${jchatmind.memory.redis.recent-window-size:20}") int recentWindowSize,
            @Value("${jchatmind.memory.redis.summary-max-chars:2000}") int summaryMaxChars,
            @Value("${jchatmind.memory.redis.llm-summary-enabled:true}") boolean llmSummaryEnabled,
            @Value("${jchatmind.memory.redis.llm-summary-model:deepseek-chat}") String llmSummaryModel
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.chatClientRegistry = chatClientRegistry;
        this.ttlDays = ttlDays;
        this.maxMessagesPerSession = maxMessagesPerSession;
        this.summaryTriggerMessages = summaryTriggerMessages;
        this.recentWindowSize = recentWindowSize;
        this.summaryMaxChars = summaryMaxChars;
        this.llmSummaryEnabled = llmSummaryEnabled;
        this.llmSummaryModel = llmSummaryModel;
    }

    @Override
    public List<ChatMessageDTO> getRecentMessages(String sessionId, int limit) {
        if (!StringUtils.hasText(sessionId) || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            List<ChatMessageDTO> allMessages = readAllMessages(sessionId);
            if (CollectionUtils.isEmpty(allMessages)) {
                return Collections.emptyList();
            }
            // 关键逻辑：优先保留摘要消息 + 最近窗口消息，兼顾上下文完整性和 Token 控制
            ChatMessageDTO summaryMessage = extractSummaryMessage(allMessages);
            List<ChatMessageDTO> normalMessages = allMessages.stream()
                    .filter(msg -> !isSummaryMessage(msg))
                    .toList();

            int normalLimit = summaryMessage == null ? limit : Math.max(0, limit - 1);
            int start = Math.max(0, normalMessages.size() - normalLimit);
            List<ChatMessageDTO> result = new ArrayList<>();
            if (summaryMessage != null) {
                result.add(summaryMessage);
            }
            result.addAll(normalMessages.subList(start, normalMessages.size()));
            return result;
        } catch (Exception e) {
            log.warn("Read short-term memory from Redis failed, sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void appendMessage(ChatMessageDTO chatMessageDTO) {
        if (chatMessageDTO == null || !StringUtils.hasText(chatMessageDTO.getSessionId())) {
            return;
        }
        String key = buildKey(chatMessageDTO.getSessionId());
        try {
            String serialized = objectMapper.writeValueAsString(chatMessageDTO);
            stringRedisTemplate.opsForList().rightPush(key, serialized);
            // 关键逻辑：每次新增消息后都触发压缩检查，形成“滑动窗口 + 历史摘要”结构
            compactMemoryIfNeeded(chatMessageDTO.getSessionId());
            refreshTtl(key);
        } catch (Exception e) {
            log.warn("Append short-term memory to Redis failed, sessionId={}", chatMessageDTO.getSessionId(), e);
        }
    }

    @Override
    public void warmupMessages(String sessionId, List<ChatMessageDTO> messages) {
        if (!StringUtils.hasText(sessionId) || CollectionUtils.isEmpty(messages)) {
            return;
        }
        String key = buildKey(sessionId);
        try {
            stringRedisTemplate.delete(key);

            int start = Math.max(0, messages.size() - maxMessagesPerSession);
            List<String> serialized = new ArrayList<>(messages.size() - start);
            for (int i = start; i < messages.size(); i++) {
                serialized.add(objectMapper.writeValueAsString(messages.get(i)));
            }
            if (!serialized.isEmpty()) {
                stringRedisTemplate.opsForList().rightPushAll(key, serialized);
                compactMemoryIfNeeded(sessionId);
                refreshTtl(key);
            }
        } catch (Exception e) {
            log.warn("Warmup short-term memory in Redis failed, sessionId={}", sessionId, e);
        }
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private void refreshTtl(String key) {
        if (ttlDays > 0) {
            stringRedisTemplate.expire(key, Duration.ofDays(ttlDays));
        }
    }

    private List<ChatMessageDTO> readAllMessages(String sessionId) {
        String key = buildKey(sessionId);
        List<String> serializedMessages = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtils.isEmpty(serializedMessages)) {
            return Collections.emptyList();
        }
        List<ChatMessageDTO> result = new ArrayList<>(serializedMessages.size());
        for (String item : serializedMessages) {
            try {
                result.add(objectMapper.readValue(item, ChatMessageDTO.class));
            } catch (JsonProcessingException parseException) {
                log.warn("Redis memory parse failed, sessionId={}, payload={}", sessionId, item, parseException);
            }
        }
        return result;
    }

    private void compactMemoryIfNeeded(String sessionId) throws JsonProcessingException {
        List<ChatMessageDTO> allMessages = readAllMessages(sessionId);
        if (CollectionUtils.isEmpty(allMessages)) {
            return;
        }

        ChatMessageDTO summaryMessage = extractSummaryMessage(allMessages);
        String existingSummaryText = summaryMessage == null ? "" : summaryMessage.getContent();
        List<ChatMessageDTO> normalMessages = allMessages.stream()
                .filter(msg -> !isSummaryMessage(msg))
                .toList();

        if (normalMessages.size() >= summaryTriggerMessages) {
            // 关键逻辑：仅压缩窗口外历史消息，最近窗口保持原文，保证模型能继续利用最新上下文
            int split = Math.max(0, normalMessages.size() - recentWindowSize);
            List<ChatMessageDTO> historicalMessages = normalMessages.subList(0, split);
            List<ChatMessageDTO> recentMessages = normalMessages.subList(split, normalMessages.size());

            String mergedSummary = buildMergedSummary(existingSummaryText, historicalMessages);
            ChatMessageDTO mergedSummaryMessage = ChatMessageDTO.builder()
                    .sessionId(sessionId)
                    .role(ChatMessageDTO.RoleType.SYSTEM)
                    .content(mergedSummary)
                    .build();

            List<ChatMessageDTO> compacted = new ArrayList<>(1 + recentMessages.size());
            compacted.add(mergedSummaryMessage);
            compacted.addAll(recentMessages);
            rewriteMessages(sessionId, compacted);
            return;
        }

        // 兜底控制：即使未触发摘要，也保证缓存不超过 hard limit
        enforceMaxMessages(sessionId, summaryMessage, normalMessages);
    }

    private void enforceMaxMessages(String sessionId,
            ChatMessageDTO summaryMessage,
            List<ChatMessageDTO> normalMessages) throws JsonProcessingException {
        int allowedNormalMessages = summaryMessage == null ? maxMessagesPerSession : Math.max(0, maxMessagesPerSession - 1);
        if (normalMessages.size() <= allowedNormalMessages) {
            return;
        }

        int start = normalMessages.size() - allowedNormalMessages;
        List<ChatMessageDTO> keep = new ArrayList<>();
        if (summaryMessage != null) {
            keep.add(summaryMessage);
        }
        keep.addAll(normalMessages.subList(start, normalMessages.size()));
        rewriteMessages(sessionId, keep);
    }

    private void rewriteMessages(String sessionId, List<ChatMessageDTO> messages) throws JsonProcessingException {
        String key = buildKey(sessionId);
        stringRedisTemplate.delete(key);
        List<String> serialized = new ArrayList<>(messages.size());
        for (ChatMessageDTO message : messages) {
            serialized.add(objectMapper.writeValueAsString(message));
        }
        if (!serialized.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(key, serialized);
        }
        refreshTtl(key);
    }

    private ChatMessageDTO extractSummaryMessage(List<ChatMessageDTO> messages) {
        return messages.stream()
                .filter(this::isSummaryMessage)
                .findFirst()
                .orElse(null);
    }

    private boolean isSummaryMessage(ChatMessageDTO message) {
        return message != null
                && message.getRole() == ChatMessageDTO.RoleType.SYSTEM
                && StringUtils.hasText(message.getContent())
                && message.getContent().startsWith(SUMMARY_PREFIX);
    }

    private String buildMergedSummary(String existingSummaryText, List<ChatMessageDTO> historicalMessages) {
        // 关键逻辑：优先使用 LLM 进行语义压缩，失败时回退到规则摘要，保障可用性。
        String llmSummary = buildMergedSummaryByLlm(existingSummaryText, historicalMessages);
        if (StringUtils.hasText(llmSummary)) {
            return llmSummary;
        }
        return buildMergedSummaryByRule(existingSummaryText, historicalMessages);
    }

    private String buildMergedSummaryByLlm(String existingSummaryText, List<ChatMessageDTO> historicalMessages) {
        if (!llmSummaryEnabled || CollectionUtils.isEmpty(historicalMessages)) {
            return null;
        }
        ChatClient chatClient = chatClientRegistry.get(llmSummaryModel);
        if (chatClient == null) {
            log.warn("LLM summary model not found in registry: {}", llmSummaryModel);
            return null;
        }

        String userPrompt = buildLlmSummaryUserPrompt(existingSummaryText, historicalMessages);
        try {
            Prompt prompt = Prompt.builder()
                    .messages(List.of(
                            new SystemMessage("""
                                    你是对话记忆压缩器。请将历史对话压缩成用于后续问答的短摘要。
                                    要求：
                                    1) 保留用户偏好、事实约束、任务目标、已完成步骤、未完成事项。
                                    2) 删除寒暄、重复和无关细节。
                                    3) 使用简洁中文要点，控制在 8-12 条以内。
                                    4) 输出纯文本，不要 Markdown 标题。
                                    """),
                            new UserMessage(userPrompt)
                    ))
                    .build();
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return null;
            }
            String content = response.getResult().getOutput().getText();
            if (!StringUtils.hasText(content)) {
                return null;
            }
            return normalizeSummaryContent(content);
        } catch (Exception e) {
            log.warn("LLM summary generation failed, fallback to rule summary", e);
            return null;
        }
    }

    private String buildLlmSummaryUserPrompt(String existingSummaryText, List<ChatMessageDTO> historicalMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("已有摘要：\n");
        if (StringUtils.hasText(existingSummaryText)) {
            builder.append(existingSummaryText).append('\n');
        } else {
            builder.append("(无)\n");
        }
        builder.append("\n新增历史消息：\n");
        for (ChatMessageDTO message : historicalMessages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append("- ")
                    .append(Objects.requireNonNullElse(message.getRole(), ChatMessageDTO.RoleType.USER).getRole())
                    .append(": ")
                    .append(message.getContent().replace('\n', ' ').trim())
                    .append('\n');
            if (builder.length() >= summaryMaxChars * 2) {
                break;
            }
        }
        return builder.toString();
    }

    private String normalizeSummaryContent(String summaryBody) {
        String cleaned = summaryBody.trim();
        if (!cleaned.startsWith(SUMMARY_PREFIX)) {
            cleaned = SUMMARY_PREFIX + "\n" + cleaned;
        }
        if (cleaned.length() > summaryMaxChars) {
            return cleaned.substring(0, summaryMaxChars);
        }
        return cleaned;
    }

    private String buildMergedSummaryByRule(String existingSummaryText, List<ChatMessageDTO> historicalMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append(SUMMARY_PREFIX).append('\n');

        if (StringUtils.hasText(existingSummaryText)) {
            String previous = existingSummaryText.startsWith(SUMMARY_PREFIX)
                    ? existingSummaryText.substring(SUMMARY_PREFIX.length()).trim()
                    : existingSummaryText.trim();
            if (StringUtils.hasText(previous)) {
                builder.append("已有摘要：").append(previous).append('\n');
            }
        }

        builder.append("新增历史要点：").append('\n');
        for (ChatMessageDTO message : historicalMessages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append("- ")
                    .append(Objects.requireNonNullElse(message.getRole(), ChatMessageDTO.RoleType.USER).getRole())
                    .append(": ")
                    .append(message.getContent().replace('\n', ' ').trim())
                    .append('\n');
            if (builder.length() >= summaryMaxChars) {
                break;
            }
        }
        if (builder.length() > summaryMaxChars) {
            return builder.substring(0, summaryMaxChars);
        }
        return builder.toString();
    }
}
