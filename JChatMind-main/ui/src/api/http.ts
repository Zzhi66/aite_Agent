import { message } from "antd";
import {
  clearAuthStorage,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from "../utils/token.ts";

// API 响应类型定义，匹配后端 ApiResponse 结构
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

// 请求配置选项
export interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | null | undefined>;
  /** 跳过自动注入 Authorization（用于登录/注册等公开接口） */
  skipAuth?: boolean;
  /** 内部标记：401 后已重试过，避免无限循环 */
  _retried?: boolean;
}

// API 基础路径（可以根据环境变量配置）
export const BASE_URL = "http://localhost:8080/api";

/** SSE 等非 /api 前缀的后端根地址 */
export const SERVER_ORIGIN = "http://localhost:8080";

// 并发 401 时共享同一次刷新请求
let refreshPromise: Promise<boolean> | null = null;

/**
 * 使用 Refresh Token 换取新的 Access Token
 */
async function tryRefreshToken(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${BASE_URL}/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) {
          return false;
        }
        const apiResponse: ApiResponse<{
          accessToken: string;
          refreshToken: string;
        }> = await response.json();
        if (apiResponse.code !== 200 || !apiResponse.data) {
          return false;
        }
        setTokens(
          apiResponse.data.accessToken,
          apiResponse.data.refreshToken,
        );
        return true;
      } catch {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }

  return refreshPromise;
}

/** 登录过期：清除凭证并跳转登录页 */
function redirectToLogin(): void {
  clearAuthStorage();
  const loginPath = "/login";
  if (window.location.pathname !== loginPath) {
    window.location.href = loginPath;
  }
}

/**
 * 构建完整的 URL（包含查询参数）
 */
function buildUrl(
  url: string,
  params?: Record<string, string | number | boolean | null | undefined>,
): string {
  const fullUrl = `${BASE_URL}${url}`;

  if (!params || Object.keys(params).length === 0) {
    return fullUrl;
  }

  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined) {
      searchParams.append(key, String(value));
    }
  });

  const queryString = searchParams.toString();
  return queryString ? `${fullUrl}?${queryString}` : fullUrl;
}

/**
 * 处理响应（含 401 刷新与重试）
 */
async function handleResponse<T>(
  response: Response,
  retryRequest?: () => Promise<Response>,
): Promise<ApiResponse<T>> {
  // 401：尝试刷新 Token 后重试一次
  if (response.status === 401 && retryRequest) {
    const refreshed = await tryRefreshToken();
    if (refreshed) {
      const retryResponse = await retryRequest();
      return handleResponse<T>(retryResponse);
    }
    redirectToLogin();
    throw new Error("登录已过期，请重新登录");
  }

  if (response.status === 401) {
    redirectToLogin();
    throw new Error("登录已过期，请重新登录");
  }

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const data: ApiResponse<T> = await response.json();

  // 检查业务状态码
  if (data.code !== 200) {
    message.error(data.message || "请求失败");
    throw new Error(data.message || "请求失败");
  }

  return data;
}

/**
 * 封装的 fetch 请求函数
 */
async function request<T = unknown>(
  url: string,
  options: RequestOptions = {},
): Promise<T> {
  const { params, headers, skipAuth, _retried, ...restOptions } = options;

  const fullUrl = buildUrl(url, params);

  const buildHeaders = (): HeadersInit => {
    const token = skipAuth ? null : getAccessToken();
    return {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    };
  };

  const doFetch = () =>
    fetch(fullUrl, {
      ...restOptions,
      headers: buildHeaders(),
    });

  try {
    const response = await doFetch();
    const canRetry = !skipAuth && !_retried;
    const apiResponse = await handleResponse<T>(
      response,
      canRetry
        ? async () => {
            const retryResponse = await doFetch();
            return retryResponse;
          }
        : undefined,
    );
    return apiResponse.data;
  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error("网络请求失败");
  }
}

/**
 * GET 请求
 */
export function get<T = unknown>(
  url: string,
  params?: Record<string, string | number | boolean | null | undefined>,
  options?: Omit<RequestOptions, "method" | "body" | "params">,
): Promise<T> {
  return request<T>(url, {
    ...options,
    method: "GET",
    params,
  });
}

/**
 * POST 请求
 */
export function post<T = unknown>(
  url: string,
  data?: unknown,
  options?: Omit<RequestOptions, "method" | "body">,
): Promise<T> {
  return request<T>(url, {
    ...options,
    method: "POST",
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * PUT 请求
 */
export function put<T = unknown>(
  url: string,
  data?: unknown,
  options?: Omit<RequestOptions, "method" | "body">,
): Promise<T> {
  return request<T>(url, {
    ...options,
    method: "PUT",
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * PATCH 请求
 */
export function patch<T = unknown>(
  url: string,
  data?: unknown,
  options?: Omit<RequestOptions, "method" | "body">,
): Promise<T> {
  return request<T>(url, {
    ...options,
    method: "PATCH",
    body: data ? JSON.stringify(data) : undefined,
  });
}

/**
 * DELETE 请求
 */
export function del<T = unknown>(
  url: string,
  params?: Record<string, string | number | boolean | null | undefined>,
  options?: Omit<RequestOptions, "method" | "body" | "params">,
): Promise<T> {
  return request<T>(url, {
    ...options,
    method: "DELETE",
    params,
  });
}

// 导出默认对象，方便使用
export default {
  get,
  post,
  put,
  patch,
  delete: del,
};
