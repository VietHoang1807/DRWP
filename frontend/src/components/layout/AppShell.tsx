import { Suspense } from 'react';
import { Outlet } from 'react-router';
import { useIsFetching } from '@tanstack/react-query';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { PageSkeleton } from '../ui/PageSkeleton';
import { useSession, useTenantId } from '../../auth/session';

/** Thanh mảnh khi làm mới dữ liệu đã có (lần tải đầu dùng skeleton của từng khối). */
function RefetchBar() {
  const fetching = useIsFetching();
  return fetching > 0 ? <div role="progressbar" aria-label="Đang làm mới" className="refetch-bar fixed top-16 inset-x-0 h-0.5 z-50" /> : null;
}

export function AppShell() {
  const session = useSession()!;
  const tenantId = useTenantId();
  return (
    <div className="min-h-screen bg-surface">
      <Header session={session} tenantId={tenantId} />
      <RefetchBar />
      <Sidebar platformAdmin={session.platformAdmin} hasTenant={!!tenantId} />
      <main className="pl-64 pt-16">
        <div className="px-6 lg:px-8 py-6 max-w-[1440px] enter">
          <Suspense fallback={<PageSkeleton />}>
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
