import { config } from './config';
import { sessionStore } from '../auth/session';

export interface Violation {
  field: string;
  code?: string;
  message: string;
}

/** RFC 9457 Problem Details từ backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly violations: Violation[];
  readonly correlationId?: string;

  constructor(status: number, code: string, message: string, violations: Violation[] = [], correlationId?: string) {
    super(message);
    this.status = status;
    this.code = code;
    this.violations = violations;
    this.correlationId = correlationId;
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Query;
  /** false cho /api/platform/** và /api/me/** (không cần X-Tenant-Id) */
  tenant?: boolean;
  raw?: boolean;
}

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

export function buildUrl(path: string, query?: Query) {
  const url = new URL(config.apiBase + path, window.location.origin);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    }
  }
  return config.apiBase ? url.toString() : url.pathname + url.search;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { session, tenantId } = sessionStore.get();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (session) headers.Authorization = `Bearer ${session.token}`;
  if (opts.tenant !== false && tenantId) headers['X-Tenant-Id'] = tenantId;
  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(buildUrl(path, opts.query), { method: opts.method ?? 'GET', headers, body });
  if (res.status === 401) {
    onUnauthorized();
    throw new ApiError(401, 'UNAUTHENTICATED', 'Phiên đăng nhập đã hết hạn');
  }
  if (!res.ok) {
    let problem: { code?: string; detail?: string; title?: string; violations?: Violation[]; correlationId?: string } = {};
    try {
      problem = await res.json();
    } catch {
      // body rỗng (ví dụ 403 của Spring Security)
    }
    throw new ApiError(res.status, problem.code ?? `HTTP_${res.status}`, problem.detail ?? problem.title ?? res.statusText,
      problem.violations ?? [], problem.correlationId);
  }
  if (opts.raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const http = {
  get: <T>(path: string, query?: Query, tenant = true) => request<T>(path, { query, tenant }),
  post: <T>(path: string, body?: unknown, tenant = true) => request<T>(path, { method: 'POST', body, tenant }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

/** Thông điệp tiếng Việt cho lỗi thường gặp. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.status) {
      case 403:
        return err.code === 'TENANT_ACCESS_DENIED' ? 'Bạn không thuộc đơn vị này.' : 'Bạn không có quyền thực hiện thao tác này.';
      case 404:
        return 'Không tìm thấy dữ liệu hoặc bạn không có quyền xem.';
      case 409:
        return `Xung đột dữ liệu: ${err.message}`;
      case 422:
        return 'Dữ liệu chưa hợp lệ, vui lòng kiểm tra các trường được đánh dấu.';
      default:
        return err.message || 'Đã có lỗi xảy ra.';
    }
  }
  if (err instanceof TypeError) return 'Không kết nối được máy chủ. Backend đã chạy chưa?';
  return 'Đã có lỗi xảy ra.';
}
