import { lazy, Suspense } from 'react';
import { Notice } from '../components/ui/Primitives';
import { PageSkeleton } from '../components/ui/PageSkeleton';

const BatchJobMonitoring = lazy(() => import('./legacy/BatchJobMonitoring').then((m) => ({ default: m.BatchJobMonitoring })));
const AuthSources = lazy(() => import('./legacy/AuthSources').then((m) => ({ default: m.AuthSources })));

/** Màn hình thiết kế sẵn nhưng backend chưa có API: giữ giao diện, dữ liệu là minh hoạ. */
export default function ComingSoon({ kind }: { kind: 'batch' | 'auth' }) {
  return (
    <>
      <div className="mb-4" data-testid="coming-soon">
        <Notice tone="warning">
          <b>Sắp có (Phase sau).</b>{' '}
          {kind === 'batch'
            ? 'Backend chưa có API giám sát batch job. Dữ liệu dưới đây chỉ là minh hoạ giao diện.'
            : 'Đăng nhập hiện cấu hình qua Keycloak. Màn hình quản lý nguồn xác thực sẽ nối API ở phase sau; dữ liệu dưới đây là minh hoạ.'}
        </Notice>
      </div>
      <div className="pointer-events-none opacity-70 select-none" aria-hidden="true">
        <Suspense fallback={<PageSkeleton />}>{kind === 'batch' ? <BatchJobMonitoring /> : <AuthSources />}</Suspense>
      </div>
    </>
  );
}
