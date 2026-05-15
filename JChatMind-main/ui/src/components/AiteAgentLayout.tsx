import { Routes, Route } from "react-router-dom";
import Layout from "../layout/Layout.tsx";
import Sidebar from "../layout/Sidebar.tsx";
import SideMenu from "./SideMenu.tsx";
import Content from "../layout/Content.tsx";
import AgentChatView from "./views/AgentChatView.tsx";
import KnowledgeBaseView from "./views/KnowledgeBaseView.tsx";

/** 主应用布局：侧边栏 + 对话/知识库路由 */
export default function AiteAgentLayout() {
  return (
    <Layout>
      <Sidebar>
        <SideMenu />
      </Sidebar>
      <Content>
        <Routes>
          <Route path="/" element={<AgentChatView />} />
          <Route path="/agent" element={<AgentChatView />} />
          <Route path="/chat" element={<AgentChatView />} />
          <Route path="/chat/:chatSessionId" element={<AgentChatView />} />
          <Route path="/knowledge-base" element={<KnowledgeBaseView />} />
          <Route
            path="/knowledge-base/:knowledgeBaseId"
            element={<KnowledgeBaseView />}
          />
        </Routes>
      </Content>
    </Layout>
  );
}
