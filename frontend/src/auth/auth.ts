import { config } from '../lib/config';
import { buildUrl } from '../lib/http';
import { sessionFromToken, sessionStore } from './session';

/**
 * Hai cách đăng nhập (chọn bằng VITE_AUTH_MODE):
 *  - dev : POST /dev/token của backend (profile dev). Chỉ nhớ tham số đăng nhập (subject) trong sessionStorage
 *          để tự xin lại token khi tải lại trang; token chỉ ở bộ nhớ.
 *  - oidc: Keycloak Authorization Code + PKCE (client public drwp-web). Token chỉ ở bộ nhớ; tải lại trang thì
 *          chuyển hướng lại Keycloak (phiên SSO của Keycloak giúp không phải nhập lại mật khẩu).
 */

const DEV_KEY = 'drwp.dev-login';
const PKCE_KEY = 'drwp.pkce';

export interface DevLogin {
  subject: string;
  username?: string;
  platformAdmin: boolean;
}

export async function devLogin(input: DevLogin) {
  const res = await fetch(buildUrl('/dev/token'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(res.status === 404 || res.status === 403
      ? 'Backend chưa bật chế độ dev (profile dev).'
      : `Đăng nhập thất bại (HTTP ${res.status})`);
  }
  const body = (await res.json()) as { access_token: string };
  sessionStore.setSession(sessionFromToken(body.access_token));
  try {
    sessionStorage.setItem(DEV_KEY, JSON.stringify(input));
  } catch {
    // bỏ qua
  }
}

// ------------------------------------------------------------------ OIDC PKCE

const oidcEndpoint = (p: string) => `${config.oidcIssuer}/protocol/openid-connect/${p}`;
const redirectUri = () => `${window.location.origin}/auth/callback`;

function base64Url(bytes: ArrayBuffer | Uint8Array) {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = '';
  arr.forEach((b) => (s += String.fromCharCode(b)));
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function oidcLogin(returnTo = '/app') {
  const verifier = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  const state = base64Url(crypto.getRandomValues(new Uint8Array(16)));
  const challenge = base64Url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
  sessionStorage.setItem(PKCE_KEY, JSON.stringify({ verifier, state, returnTo }));
  const url = new URL(oidcEndpoint('auth'));
  url.search = new URLSearchParams({
    client_id: config.oidcClientId,
    response_type: 'code',
    scope: 'openid profile',
    redirect_uri: redirectUri(),
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
  }).toString();
  window.location.assign(url.toString());
}

/** Xử lý /auth/callback: đổi code lấy token. @returns đường dẫn quay lại */
export async function oidcCallback(search: string): Promise<string> {
  const params = new URLSearchParams(search);
  const saved = JSON.parse(sessionStorage.getItem(PKCE_KEY) ?? '{}') as { verifier?: string; state?: string; returnTo?: string };
  sessionStorage.removeItem(PKCE_KEY);
  if (!params.get('code') || !saved.verifier || params.get('state') !== saved.state) {
    throw new Error('Phản hồi đăng nhập không hợp lệ (state không khớp).');
  }
  const res = await fetch(oidcEndpoint('token'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.oidcClientId,
      code: params.get('code')!,
      redirect_uri: redirectUri(),
      code_verifier: saved.verifier,
    }),
  });
  if (!res.ok) throw new Error(`Không đổi được mã đăng nhập (HTTP ${res.status})`);
  const body = (await res.json()) as { access_token: string };
  sessionStore.setSession(sessionFromToken(body.access_token));
  return saved.returnTo ?? '/app';
}

/** Khôi phục phiên khi tải lại trang (chỉ chế độ dev). */
export async function restoreSession(): Promise<boolean> {
  if (sessionStore.get().session) return true;
  if (config.authMode !== 'dev') return false;
  try {
    const raw = sessionStorage.getItem(DEV_KEY);
    if (!raw) return false;
    await devLogin(JSON.parse(raw) as DevLogin);
    return true;
  } catch {
    return false;
  }
}

export function logout() {
  try {
    sessionStorage.removeItem(DEV_KEY);
  } catch {
    // bỏ qua
  }
  sessionStore.clear();
  if (config.authMode === 'oidc') {
    const url = new URL(oidcEndpoint('logout'));
    url.search = new URLSearchParams({ client_id: config.oidcClientId, post_logout_redirect_uri: `${window.location.origin}/` }).toString();
    window.location.assign(url.toString());
  }
}
