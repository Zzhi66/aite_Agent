/** Token 在 localStorage 中的键名 */
const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";
const USER_INFO_KEY = "userInfo";

/** 当前登录用户摘要信息 */
export interface StoredUserInfo {
  userId: string;
  username: string;
  nickname?: string;
  role?: string;
}

/** 读取 Access Token */
export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

/** 读取 Refresh Token */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/** 保存双 Token */
export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

/** 保存用户信息摘要 */
export function setUserInfo(user: StoredUserInfo): void {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
}

/** 读取用户信息摘要 */
export function getUserInfo(): StoredUserInfo | null {
  const raw = localStorage.getItem(USER_INFO_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredUserInfo;
  } catch {
    return null;
  }
}

/** 是否已保存 Access Token（用于快速判断登录态） */
export function hasAccessToken(): boolean {
  return !!getAccessToken();
}

/** 构建带 Bearer 的请求头（供 fetch / EventSource 等使用） */
export function getBearerAuthHeaders(): HeadersInit {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 清除所有认证相关本地数据 */
export function clearAuthStorage(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_INFO_KEY);
}
