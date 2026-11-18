import { StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import './index.css';
import { router } from './router';
import { ApiError, setUnauthorizedHandler } from './lib/http';
import { sessionStore } from './auth/session';

function onApiError(err: unknown) {
  // Tenant không còn hợp lệ (bị tạm dừng / mất membership): quay lại chọn đơn vị
  if (err instanceof ApiError && err.status === 403 && (err.code === 'TENANT_ACCESS_DENIED' || err.code === 'TENANT_ISOLATION')) {
    sessionStore.setTenant(null);
    void router.navigate('/select-tenant');
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: onApiError }),
  mutationCache: new MutationCache({ onError: onApiError }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      retry: (count, err) => !(err instanceof ApiError && err.status < 500) && count < 2,
    },
  },
});

setUnauthorizedHandler(() => {
  queryClient.clear();
  sessionStore.setSession(null);
  void router.navigate(`/login?next=${encodeURIComponent(location.pathname)}`);
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router} />
      </Suspense>
    </QueryClientProvider>
  </StrictMode>,
);
