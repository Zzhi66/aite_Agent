package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.service.SseService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@AllArgsConstructor
public class SseServiceImpl implements SseService {

    private final ConcurrentMap<String, SseEmitter> clients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter connect(String chatSessionId) {
        // 1. 创建一根管道，设定超时时间为 30 分钟 (30 * 60 * 1000 毫秒)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        // 2. 存入 Map，key 就是聊天 ID，value 就是这根管道
        clients.put(chatSessionId, emitter);

        try {
            // 3. 发送一条 "init" 类型的事件，告诉前端：连接成功，可以开始发消息了
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data("connected")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        emitter.onCompletion(() -> {
            clients.remove(chatSessionId);
        });
        emitter.onTimeout(() -> clients.remove(chatSessionId));
        emitter.onError((error) -> clients.remove(chatSessionId));

        return emitter;
    }

    @Override
    public void send(String chatSessionId, SseMessage message) {
        // 4. 根据聊天 ID 找到对应的管道
        SseEmitter emitter = clients.get(chatSessionId);
        // 如果没有找到对应的管道，说明客户端已经断开连接了
        if (emitter != null) {
            try {
                // 将消息转换为字符串
                String sseMessageStr = objectMapper.writeValueAsString(message);
                // 5. 发送消息
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(sseMessageStr)
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("No client found for chatSessionId: " + chatSessionId);
        }
    }
}
