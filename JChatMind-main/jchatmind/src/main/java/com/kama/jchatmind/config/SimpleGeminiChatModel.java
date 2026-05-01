package com.kama.jchatmind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 手写一个最简易的支持 Google Gemini (OpenAI 兼容协议) 的 ChatModel 实现
 * 以绕过 spring-ai-openai 难以通过阿里云 Maven 仓库下载的问题。
 */
@Component
public class SimpleGeminiChatModel implements ChatModel {

    private final RestClient restClient;

    @Value("${spring.ai.openai.api-key:null}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gemini-2.5-pro}")
    private String defaultModel;

    public SimpleGeminiChatModel(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if ("null".equals(apiKey) || apiKey.isEmpty()) {
            throw new RuntimeException("Gemini API Key is not configured.");
        }

        // 构建 OpenAI 格式的请求
        List<Map<String, String>> messages = prompt.getInstructions().stream().map(msg -> {
            Map<String, String> m = new HashMap<>();

            if (msg instanceof SystemMessage) {
                m.put("role", "system");
            } else if (msg instanceof AssistantMessage) {
                m.put("role", "assistant");
            } else {
                m.put("role", "user"); // Default to user message
            }
            m.put("content", msg.getText());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("model", defaultModel);
        request.put("messages", messages);
        // 为了简单容错，不传递其他 options 给 API

        // 调用
        try {
            Map response = restClient.post()
                    .uri(baseUrl + "chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) firstChoice.get("message");
                    String content = message != null ? message.get("content") : "";

                    AssistantMessage assistantMessage = new AssistantMessage(content);
                    Generation generation = new Generation(assistantMessage);
                    return new ChatResponse(Collections.singletonList(generation));
                }
            }
            throw new RuntimeException("Empty response from Gemini API");
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    @Override
    public ChatOptions getDefaultOptions() {
        // Return default Spring AI Open Ai Chat Options implementation if necessary or
        // null
        return null;
    }
}
