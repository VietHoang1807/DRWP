import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useSession, useTenantId } from './session';
import { restoreSession } from './auth';
import { PageSkeleton } from '../components/ui/PageSkeleton';

export function RequireAuth({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  const [restoring, setRestoring] = useState(!session);

  useEffect(() => {
    if (session) return;
    let alive = true;
    restoreSession().finally(() => alive && setRestoring(false));
    return () => {
      alive = false;
    };
  }, [session]);

  if (session) return <>{children}</>;
  if (restoring) return <div className="pt-16"><PageSkeleton /></div>;
  return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
}

export function RequireTenant({ children }: { children: ReactNode }) {
  const tenantId = useTenantId();
  return tenantId ? <>{children}</> : <Navigate to="/select-tenant" replace />;
}

export function RequirePlatformAdmin({ children }: { children: ReactNode }) {
  const session = useSession();
  return session?.platformAdmin ? <>{children}</> : <Navigate to="/app" replace />;
}
