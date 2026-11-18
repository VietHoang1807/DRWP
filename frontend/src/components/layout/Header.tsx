import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { Icon } from '../ui/Primitives';
import { useMyTenants } from '../../api/platform';
import { useUnreadCount } from '../../api/misc';
import { sessionStore, type Session } from '../../auth/session';
import { logout } from '../../auth/auth';

function Bell() {
  const { data } = useUnreadCount();
  const n = data?.unread ?? 0;
  return (
    <Link to="/app/notifications" aria-label={`Thông báo, ${n} chưa đọc`}
      className="relative w-9 h-9 flex items-center justify-center rounded-lg text-on-surface-variant hover:bg-surface-container-low">
      <Icon name="notifications" />
      {n > 0 ? (
        <span className="absolute top-1 right-1 min-w-4 h-4 px-1 rounded-full bg-error text-white text-[10px] font-bold flex items-center justify-center">
          {n > 99 ? '99+' : n}
        </span>
      ) : null}
    </Link>
  );
}

export function Header({ session, tenantId }: { session: Session; tenantId: string | null }) {
  const { data: tenants } = useMyTenants();
  const [menu, setMenu] = useState(false);
  const qc = useQueryClient();
  const navigate = useNavigate();
  const current = tenants?.find((t) => t.tenantId === tenantId);

  const switchTenant = (id: string) => {
    if (!id || id === tenantId) return;
    qc.removeQueries({ queryKey: ['t'] }); // bỏ cache của đơn vị cũ
    sessionStore.setTenant(id);
    navigate('/app');
  };

  return (
    <header className="fixed top-0 inset-x-0 h-16 z-50 bg-surface-container-lowest/95 backdrop-blur border-b border-outline-variant/40">
      <div className="h-full px-5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <Link to="/" className="flex items-center gap-2">
            <span className="w-8 h-8 rounded-lg bg-primary text-white flex items-center justify-center"><Icon name="hub" className="text-[18px]" /></span>
            <span className="font-bold text-primary tracking-tight text-lg" style={{ fontFamily: 'Instrument Sans, Inter, sans-serif' }}>DRWP</span>
          </Link>
          {tenants && tenants.length > 0 ? (
            <label className="hidden md:flex items-center gap-2 pl-4 border-l border-outline-variant/60">
              <Icon name="apartment" className="text-[18px] text-outline" />
              <span className="sr-only">Đơn vị đang làm việc</span>
              <select data-testid="tenant-switcher" value={tenantId ?? ''} onChange={(e) => switchTenant(e.target.value)}
                className="h-9 rounded-lg border border-outline-variant/60 bg-surface-container-lowest px-2 text-sm font-medium max-w-64">
                {!current ? <option value="">Chọn đơn vị…</option> : null}
                {tenants.map((t) => <option key={t.tenantId} value={t.tenantId}>{t.displayName}</option>)}
              </select>
            </label>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          {tenantId ? <Bell /> : null}
          <div className="relative">
            <button type="button" onClick={() => setMenu((m) => !m)} aria-expanded={menu} aria-haspopup="menu"
              className="flex items-center gap-2 pl-2 pr-1 h-9 rounded-lg hover:bg-surface-container-low">
              <span className="w-7 h-7 rounded-full bg-secondary-fixed text-on-secondary-fixed text-xs font-bold flex items-center justify-center uppercase">
                {session.username.slice(0, 2)}
              </span>
              <span className="hidden lg:flex flex-col text-left leading-tight">
                <span className="text-sm font-semibold">{session.username}</span>
                <span className="text-[11px] text-on-surface-variant">{session.platformAdmin ? 'Quản trị nền tảng' : current?.displayName ?? ''}</span>
              </span>
              <Icon name="expand_more" className="text-[18px] text-outline" />
            </button>
            {menu ? (
              <div role="menu" className="absolute right-0 mt-2 w-60 rounded-lg border border-outline-variant/50 bg-surface-container-lowest shadow-lg py-1 text-sm">
                <p className="px-3 py-2 text-xs text-on-surface-variant break-all">ID: {session.subject}</p>
                <button type="button" role="menuitem" onClick={() => { setMenu(false); navigate('/select-tenant'); }}
                  className="w-full text-left px-3 py-2 hover:bg-surface-container-low flex items-center gap-2"><Icon name="swap_horiz" />Đổi đơn vị</button>
                <button type="button" role="menuitem" onClick={() => { qc.clear(); logout(); navigate('/login'); }}
                  className="w-full text-left px-3 py-2 hover:bg-surface-container-low flex items-center gap-2 text-error"><Icon name="logout" />Đăng xuất</button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}
