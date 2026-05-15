import React, { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import AgentChatHistory from "./agentChatView/AgentChatHistory.tsx";
import AgentChatInput from "./agentChatView/AgentChatInput.tsx";
import {
  createChatMessage,
  createChatSession,
  getChatMessagesBySessionId,
  getChatSession,
} from "../../api/api.ts";
import { SERVER_ORIGIN } from "../../api/http.ts";
import { getAccessToken } from "../../utils/token.ts";
import { useAgents } from "../../hooks/useAgents.ts";
import { useChatSessions } from "../../hooks/useChatSessions.ts";
import EmptyAgentChatView from "./agentChatView/EmptyAgentChatView.tsx";
import AgentSessionsView from "./AgentSessionsView.tsx";
import type { ChatMessageVO, SseMessage, SseMessageType } from "../../types";

const AgentChatView: React.FC = () => {
  const { chatSessionId } = useParams<{ chatSessionId: string }>();
  const navigate = useNavigate();
  const { state } = useLocation();
  const [loading, setLoading] = useState(false);
  const { agents } = useAgents();
  const { refreshChatSessions } = useChatSessions();

  const [messages, setMessages] = useState<ChatMessageVO[]>([]);

  const addMessage = (message: ChatMessageVO) => {
    setMessages((prevMessages) => [...prevMessages, message]);
  };

  // 从路由 state 中读取用户在侧边栏选中的 agentId
  const [agentId, setAgentId] = useState<string>(state?.agentId ?? "");

  // 是否显示「新建对话」的空白输入页（从历史列表点击「新建对话」时为 true）
  const [showNewChat, setShowNewChat] = useState(false);

  // 当路由 state 中的 agentId 变化时（用户点击了不同智能体），同步更新并重置 showNewChat
  useEffect(() => {
    if (state?.agentId) {
      setAgentId(state.agentId);
      setShowNewChat(false); // 切换智能体后先展示历史列表
    }
  }, [state?.agentId]);

  const getChatMessages = useCallback(async () => {
    if (!chatSessionId) {
      return;
    }
    const resp = await getChatMessagesBySessionId(chatSessionId);
    setMessages(resp.chatMessages);

    const fetchData = async () => {
      const resp = await getChatSession(chatSessionId);
      // setChatSession(resp.chatSession);
      setAgentId(resp.chatSession.agentId);
    };
    fetchData().then();
  }, [chatSessionId]);

  useEffect(() => {
    if (!chatSessionId) {
      return;
    }
    getChatMessages().then();
  }, [chatSessionId, getChatMessages]);

  const handleSendMessage = async (value: string | { text: string }) => {
    // 处理 Sender 组件可能传递的不同格式
    const message = typeof value === "string" ? value : value.text;

    console.log(message);

    if (!message || !message.trim()) return;

    // 如果没有 chatSessionId，创建新会话
    if (!chatSessionId) {
      if (!agentId) {
        antdMessage.warning("请先创建一个智能体助手");
        return;
      }
      setLoading(true);
      try {
        const response = await createChatSession({
          agentId: agentId,
          title: message.slice(0, 20),
        });
        // 刷新聊天会话列表
        await refreshChatSessions();
        // 导航到新创建的会话
        navigate(`/chat/${response.chatSessionId}`, {
          replace: true,
          // 携带初始化消息
          state: {
            init: false,
            initMessage: message,
          },
        });
      } catch (error) {
        console.error("创建聊天会话失败:", error);
        antdMessage.error("创建聊天会话失败，请重试");
      } finally {
        setLoading(false);
      }
    } else {
      if (state?.init) {
        console.log("init", state.initMessage);
        await createChatMessage({
          agentId: agentId ?? "",
          sessionId: chatSessionId,
          role: "user",
          content: state.initMessage ?? "",
        });
      } else {
        console.log("ask", message);
        await createChatMessage({
          agentId: agentId ?? "",
          sessionId: chatSessionId,
          role: "user",
          content: message,
        });
      }
      await getChatMessages();
    }
  };

  const [displayAgentStatus, setDisplayAgentStatus] = useState<boolean>(false);
  const [agentStatusText, setAgentStatusText] = useState("");
  const [agentStatusType, setAgentStatusType] = useState<
    SseMessageType | undefined
  >(undefined);

  // 流式输出相关状态
  const [streamingMessageId, setStreamingMessageId] = useState<string | null>(null);
  const streamingIdRef = useRef<string | null>(null);

  useEffect(() => {
    // sse 连接处理, 不是对话消息不开连接
    if (!chatSessionId || chatSessionId === "new") {
      return;
    }
    // EventSource 不支持自定义 Header，通过 query param 传递 token
    const token = getAccessToken();
    const sseUrl = token
      ? `${SERVER_ORIGIN}/sse/connect/${chatSessionId}?token=${encodeURIComponent(token)}`
      : `${SERVER_ORIGIN}/sse/connect/${chatSessionId}`;
    const es = new EventSource(sseUrl);
    es.onmessage = (event) => {
      console.log("Received message:", event.data);
    };
    es.onerror = (error) => {
      console.error("SSE error:", error);
    };

    es.addEventListener("message", (event) => {
      // 解析 JSON
      const message = JSON.parse(event.data) as SseMessage;
      if (message.type === "AI_STREAMING_CHUNK") {
        // 流式文本块处理
        const delta = message.payload?.delta ?? "";
        if (!streamingIdRef.current) {
          // 第一个 chunk → 创建新消息条目
          const tempId = `streaming-${Date.now()}`;
          streamingIdRef.current = tempId;
          setStreamingMessageId(tempId);
          setMessages((prev) => [
            ...prev,
            {
              id: tempId,
              sessionId: chatSessionId ?? "",
              role: "assistant",
              content: delta,
            },
          ]);
        } else {
          // 后续 chunks → 追加 delta 到该消息的 content
          const currentId = streamingIdRef.current;
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === currentId
                ? { ...msg, content: msg.content + delta }
                : msg,
            ),
          );
        }
      } else if (message.type === "AI_GENERATED_CONTENT") {
        // 流式结束后收到完整消息，替换临时消息
        if (streamingIdRef.current) {
          const currentId = streamingIdRef.current;
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === currentId ? message.payload.message : msg,
            ),
          );
        } else {
          addMessage(message.payload.message);
        }
      } else if (message.type === "AI_PLANNING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_PLANNING");
      } else if (message.type === "AI_THINKING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_THINKING");
      } else if (message.type === "AI_EXECUTING") {
        setDisplayAgentStatus(true);
        setAgentStatusText(message.payload.statusText);
        setAgentStatusType("AI_EXECUTING");
      } else if (message.type === "AI_DONE") {
        streamingIdRef.current = null;
        setStreamingMessageId(null);
        setDisplayAgentStatus(false);
        setAgentStatusText("");
        setAgentStatusType(undefined);
      } else {
        throw new Error(`Unknown message type: ${message.type}`);
      }
    });

    es.addEventListener("init", (event) => {
      console.log("Received init message:", event.data);
    });

    return () => {
      console.log("Closing SSE connection.");
      es.close();
    };
  }, [chatSessionId]);

  // 如果没有 chatSessionId 或为 new，显示提示界面
  // 如果没有 chatSessionId 或为 new，根据是否有 agentId 决定渲染哪个视图
  if (!chatSessionId || chatSessionId === "new") {
    // 有 agentId 且不是「新建对话」模式 → 显示该智能体的历史列表
    const selectedAgent = agents.find((a) => a.id === agentId);
    if (agentId && selectedAgent && !showNewChat) {
      return (
        <AgentSessionsView
          agent={selectedAgent}
          onNewChat={() => setShowNewChat(true)}
        />
      );
    }
    // 无 agentId 或点击了「新建对话」→ 显示空白输入欢迎页
    return (
      <EmptyAgentChatView
        agents={agents}
        loading={loading}
        handleSendMessage={handleSendMessage}
        initialAgentId={agentId || undefined}
      />
    );
  }

  // 如果有 chatSessionId，显示正常的聊天界面
  return (
    <div className="flex flex-col h-full">
      <AgentChatHistory
        messages={messages}
        displayAgentStatus={displayAgentStatus}
        agentStatusText={agentStatusText}
        agentStatusType={agentStatusType}
        streamingMessageId={streamingMessageId}
      />
      <div className="border-t border-gray-200 p-4 bg-white">
        <AgentChatInput onSend={handleSendMessage} />
      </div>
    </div>
  );
};

export default AgentChatView;
