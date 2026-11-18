import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { oidcCallback } from '../../auth/auth';

export default function OidcCallback() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return; // StrictMode gọi effect 2 lần; code chỉ đổi được một lần
    ran.current = true;
    oidcCallback(window.location.search)
      .then((to) => navigate(to, { replace: true }))
      .catch((e: Error) => setError(e.message));
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center p-6 text-center">
      {error ? (
        <div>
          <p className="text-error">{error}</p>
          <Link to="/login" className="mt-4 inline-block underline">Đăng nhập lại</Link>
        </div>
      ) : <p className="text-on-surface-variant">Đang hoàn tất đăng nhập…</p>}
    </div>
  );
}
