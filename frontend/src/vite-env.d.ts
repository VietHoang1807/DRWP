/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 'dev' (POST /dev/token) | 'oidc' (Keycloak PKCE) */
  readonly VITE_AUTH_MODE?: string;
  /** Để trống khi dùng proxy của Vite; ví dụ https://api.drwp.vn khi deploy */
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_OIDC_ISSUER?: string;
  readonly VITE_OIDC_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
