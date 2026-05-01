package com.kama.jchatmind.service;

import com.kama.jchatmind.model.dto.ChatMessageDTO;

import java.util.List;

public interface ChatMemoryCacheService {

    List<ChatMessageDTO> getRecentMessages(String sessionId, int limit);

    void appendMessage(ChatMessageDTO chatMessageDTO);

    void warmupMessages(String sessionId, List<ChatMessageDTO> messages);
}
