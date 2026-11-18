import { useSyncExternalStore } from 'react';

/**
 * Phiên đăng nhập giữ TRONG BỘ NHỚ (tài liệu 02: không lưu token ở nơi không an toàn).
 * Chỉ tenant đang chọn (không nhạy cảm) và tham số đăng nhập dev được nhớ trong sessionStorage.
 */
export interface Session {
  token: string;
  subject: string;
  username: string;
  platformAdmin: boolean;
  expiresAt: number;
}

interface State {
  session: Session | null;
  tenantId: string | null;
}

const TENANT_KEY = 'drwp.tenant';
const listeners = new Set<() => void>();
let state: State = { session: null, tenantId: readTenant() };

function readTenant(): string | null {
  try {
    return sessionStorage.getItem(TENANT_KEY);
  } catch {
    return null;
  }
}

function emit(next: State) {
  state = next;
  listeners.forEach((l) => l());
}

export function decodeJwt(token: string): Record<string, unknown> {
  const part = token.split('.')[1] ?? '';
  const binary = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
  const json = new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0)));
  return JSON.parse(json) as Record<string, unknown>;
}

export function sessionFromToken(token: string): Session {
  const claims = decodeJwt(token);
  const roles = ((claims.realm_access as { roles?: string[] } | undefined)?.roles) ?? [];
  return {
    token,
    subject: String(claims.sub ?? ''),
    username: String(claims.preferred_username ?? claims.sub ?? ''),
    platformAdmin: roles.includes('platform-admin'),
    expiresAt: Number(claims.exp ?? 0) * 1000,
  };
}

export const sessionStore = {
  get: () => state,
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  setSession(session: Session | null) {
    emit({ ...state, session });
  },
  setTenant(tenantId: string | null) {
    try {
      if (tenantId) sessionStorage.setItem(TENANT_KEY, tenantId);
      else sessionStorage.removeItem(TENANT_KEY);
    } catch {
      // sessionStorage có thể bị chặn; tenant vẫn giữ trong bộ nhớ
    }
    emit({ ...state, tenantId });
  },
  clear() {
    this.setTenant(null);
    emit({ session: null, tenantId: null });
  },
};

export function useSession() {
  return useSyncExternalStore(sessionStore.subscribe, () => state.session);
}

export function useTenantId() {
  return useSyncExternalStore(sessionStore.subscribe, () => state.tenantId);
}
