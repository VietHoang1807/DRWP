export const config = {
  authMode: (import.meta.env.VITE_AUTH_MODE ?? 'dev') === 'oidc' ? 'oidc' : 'dev',
  apiBase: (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, ''),
  oidcIssuer: import.meta.env.VITE_OIDC_ISSUER ?? 'http://localhost:8180/realms/drwp',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'drwp-web',
} as const;
