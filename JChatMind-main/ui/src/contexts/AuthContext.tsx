import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  getCurrentUser,
  login as apiLogin,
  logout as apiLogout,
  register as apiRegister,
  type LoginRequest,
  type LoginResponse,
  type RegisterRequest,
} from "../api/auth.ts";
import {
  clearAuthStorage,
  getUserInfo,
  hasAccessToken,
  setUserInfo,
  type StoredUserInfo,
} from "../utils/token.ts";

interface AuthContextValue {
  /** 是否已完成初始化且处于已登录状态 */
  isAuthenticated: boolean;
  /** 是否正在校验本地 Token */
  loading: boolean;
  user: StoredUserInfo | null;
  login: (request: LoginRequest) => Promise<LoginResponse>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<StoredUserInfo | null>(null);

  /** 启动时用 /me 校验 Token，失败则清除本地凭证 */
  useEffect(() => {
    const initAuth = async () => {
      if (!hasAccessToken()) {
        setLoading(false);
        return;
      }

      const cached = getUserInfo();
      if (cached) {
        setUser(cached);
      }

      try {
        const me = await getCurrentUser();
        const info: StoredUserInfo = {
          userId: me.userId,
          username: me.username,
          nickname: me.nickname,
          role: me.role,
        };
        setUserInfo(info);
        setUser(info);
      } catch {
        clearAuthStorage();
        setUser(null);
      } finally {
        setLoading(false);
      }
    };

    void initAuth();
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    const data = await apiLogin(request);
    setUser({
      userId: data.userId,
      username: data.username,
      nickname: data.nickname,
      role: data.role,
    });
    return data;
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    await apiRegister(request);
  }, []);

  const logout = useCallback(() => {
    apiLogout();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: !!user && hasAccessToken(),
      loading,
      user,
      login,
      register,
      logout,
    }),
    [loading, user, login, register, logout],
  );

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  );
};

/** 获取认证上下文，必须在 AuthProvider 内使用 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth 必须在 AuthProvider 内使用");
  }
  return ctx;
}
