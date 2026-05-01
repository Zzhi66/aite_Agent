import React, { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Popconfirm, Empty } from "antd";
import {
  PlusOutlined,
  MessageOutlined,
  DeleteOutlined,
} from "@ant-design/icons";
import type { AgentVO } from "../../api/api.ts";
import { useChatSessions } from "../../hooks/useChatSessions.ts";
import { getAgentEmoji } from "../../utils";

interface AgentSessionsViewProps {
  agent: AgentVO;
  /** 点击「新建对话」时通知父组件切换到空白输入页 */
  onNewChat: () => void;
}

const AgentSessionsView: React.FC<AgentSessionsViewProps> = ({
  agent,
  onNewChat,
}) => {
  const navigate = useNavigate();
  const { chatSessions, deleteChatSession } = useChatSessions();

  // 过滤出当前智能体的历史会话
  const agentSessions = useMemo(
    () => chatSessions.filter((s) => s.agentId === agent.id),
    [chatSessions, agent.id],
  );

  const emoji = getAgentEmoji(agent.id);

  return (
    <div className="flex flex-col h-full">
      {/* 顶部标题栏 */}
      <div className="border-b border-gray-200 bg-white px-6 py-4 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-yellow-200 to-orange-200 flex items-center justify-center text-2xl shrink-0">
            {emoji}
          </div>
          <div>
            <h2 className="text-lg font-semibold text-gray-900 leading-tight">
              {agent.name}
            </h2>
            {agent.description && (
              <p className="text-sm text-gray-500 mt-0.5 line-clamp-1">
                {agent.description}
              </p>
            )}
          </div>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={onNewChat}
          className="shrink-0"
        >
          新建对话
        </Button>
      </div>

      {/* 历史会话列表 */}
      <div className="flex-1 min-h-0 overflow-y-auto p-4">
        {agentSessions.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="text-gray-400">
                  暂无对话记录，点击「新建对话」开始聊天
                </span>
              }
            />
          </div>
        ) : (
          <div className="space-y-2 max-w-2xl mx-auto">
            <p className="text-xs text-gray-400 mb-3 px-1">
              共 {agentSessions.length} 条历史对话
            </p>
            {agentSessions.map((session) => (
              <div
                key={session.id}
                onClick={() => navigate(`/chat/${session.id}`)}
                className="w-full px-4 py-3 rounded-xl bg-white border border-gray-100 cursor-pointer transition-all hover:border-indigo-200 hover:shadow-md group flex items-center gap-3"
              >
                {/* 图标 */}
                <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-blue-100 to-indigo-100 flex items-center justify-center shrink-0">
                  <MessageOutlined className="text-indigo-500 text-base" />
                </div>

                {/* 标题 */}
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-gray-800 truncate">
                    {session.title || "新对话"}
                  </div>
                </div>

                {/* 删除按钮 */}
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="shrink-0"
                >
                  <Popconfirm
                    title="确定要删除这条对话吗？"
                    description="删除后将无法恢复"
                    onConfirm={() => deleteChatSession(session.id)}
                    okText="确定"
                    cancelText="取消"
                    okType="danger"
                  >
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      className="opacity-0 group-hover:opacity-100 transition-opacity"
                      onClick={(e) => e.stopPropagation()}
                    />
                  </Popconfirm>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default AgentSessionsView;
