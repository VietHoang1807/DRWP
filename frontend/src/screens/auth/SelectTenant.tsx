import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useMyTenants } from '../../api/platform';
import { sessionStore, useSession, useTenantId } from '../../auth/session';
import { logout } from '../../auth/auth';
import { ErrorState, Icon } from '../../components/ui/Primitives';
import { ListSkeleton } from '../../components/ui/Skeleton';
import { errorMessage } from '../../lib/http';

export default function SelectTenant() {
  const session = useSession();
  const current = useTenantId();
  const { data, isPending, error, refetch } = useMyTenants();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const choose = (id: string) => {
    if (id !== current) qc.removeQueries({ queryKey: ['t'] });
    sessionStore.setTenant(id);
    navigate('/app', { replace: true });
  };

  // Chỉ một đơn vị: vào thẳng
  useEffect(() => {
    if (data?.length === 1 && !current) choose(data[0].tenantId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  return (
    <div className="min-h-screen bg-surface flex items-center justify-center p-6">
      <div className="w-full max-w-lg rounded-2xl border border-outline-variant/50 bg-surface-container-lowest shadow-sm">
        <header className="p-6 border-b border-outline-variant/40">
          <h1 className="text-xl font-semibold">Chọn đơn vị làm việc</h1>
          <p className="text-sm text-on-surface-variant mt-1">Xin chào {session?.username}. Dữ liệu mỗi đơn vị được tách riêng.</p>
        </header>
        {isPending ? <ListSkeleton rows={3} /> : error ? (
          <div className="p-6"><ErrorState message={errorMessage(error)} onRetry={() => void refetch()} /></div>
        ) : data.length === 0 ? (
          <div className="p-6 text-sm text-on-surface-variant space-y-3">
            <p>Bạn chưa là thành viên của đơn vị nào. Hãy nhờ quản trị đơn vị thêm bạn vào (theo ID: <code>{session?.subject}</code>).</p>
            {session?.platformAdmin ? (
              <Link to="/app/platform/tenants" className="inline-flex items-center gap-1 font-medium text-secondary">
                <Icon name="domain" className="text-[18px]" />Tạo đơn vị mới
              </Link>
            ) : null}
          </div>
        ) : (
          <ul className="divide-y divide-outline-variant/30">
            {data.map((t) => (
              <li key={t.tenantId}>
                <button type="button" onClick={() => choose(t.tenantId)} data-testid={`tenant-${t.tenantId}`}
                  className="w-full flex items-center gap-3 px-6 py-4 text-left hover:bg-surface-container-low">
                  <span className="w-9 h-9 rounded-lg bg-surface-container text-primary flex items-center justify-center"><Icon name="apartment" /></span>
                  <span className="flex-1">
                    <span className="block font-medium">{t.displayName}</span>
                    <span className="block text-xs text-on-surface-variant">{t.tenantId}</span>
                  </span>
                  {t.tenantId === current ? <span className="text-xs text-secondary font-semibold">Đang dùng</span> : <Icon name="chevron_right" className="text-outline" />}
                </button>
              </li>
            ))}
          </ul>
        )}
        <footer className="flex justify-between p-4 border-t border-outline-variant/40 text-sm">
          {session?.platformAdmin ? <Link to="/app/platform/tenants" className="text-secondary font-medium">Quản trị nền tảng</Link> : <span />}
          <button type="button" onClick={() => { qc.clear(); logout(); navigate('/login'); }} className="text-on-surface-variant">Đăng xuất</button>
        </footer>
      </div>
    </div>
  );
}
