import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
} from "react-router-dom";
import { Spin } from "antd";
import AiteAgentLayout from "./components/AiteAgentLayout.tsx";
import LoginView from "./components/views/LoginView.tsx";
import { AuthProvider, useAuth } from "./contexts/AuthContext.tsx";
import { ChatSessionsProvider } from "./contexts/ChatSessionsContext.tsx";

/** 受保护路由：未登录则跳转登录页 */
function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

/** 已登录用户访问 /login 时重定向到主页 */
function PublicOnlyRoute() {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginView />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route
          path="/*"
          element={
            <ChatSessionsProvider>
              <AiteAgentLayout />
            </ChatSessionsProvider>
          }
        />
      </Route>
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
