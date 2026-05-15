import { get, post } from "./http.ts";
import {
  clearAuthStorage,
  setTokens,
  setUserInfo,
  type StoredUserInfo,
} from "../utils/token.ts";

/** 登录/刷新接口返回结构（与后端 LoginResponse 对齐） */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
  nickname?: string;
  role?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
}

/** 将登录响应写入本地存储 */
export function persistLoginResponse(data: LoginResponse): void {
  setTokens(data.accessToken, data.refreshToken);
  const user: StoredUserInfo = {
    userId: data.userId,
    username: data.username,
    nickname: data.nickname,
    role: data.role,
  };
  setUserInfo(user);
}

/** 用户注册 */
export async function register(request: RegisterRequest): Promise<void> {
  await post<void>("/auth/register", request, { skipAuth: true });
}

/** 用户登录 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const data = await post<LoginResponse>("/auth/login", request, {
    skipAuth: true,
  });
  persistLoginResponse(data);
  return data;
}

/** 刷新 Token */
export async function refreshToken(
  refreshTokenValue: string,
): Promise<LoginResponse> {
  const data = await post<LoginResponse>(
    "/auth/refresh",
    { refreshToken: refreshTokenValue },
    { skipAuth: true },
  );
  persistLoginResponse(data);
  return data;
}

/** 获取当前登录用户信息 */
export async function getCurrentUser(): Promise<LoginResponse> {
  return get<LoginResponse>("/auth/me");
}

/** 退出登录：清除本地凭证 */
export function logout(): void {
  clearAuthStorage();
}
