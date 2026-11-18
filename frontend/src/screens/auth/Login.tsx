import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { config } from '../../lib/config';
import { devLogin, oidcLogin } from '../../auth/auth';
import { Icon } from '../../components/ui/Primitives';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';

/** Tài khoản mẫu do `npm run seed` tạo (chế độ dev). */
const presets = [
  { subject: 'platform-root', username: 'Quản trị nền tảng', platformAdmin: true, hint: 'Tạo đơn vị, tạm dừng/kích hoạt' },
  { subject: 'admin-demo', username: 'Quản trị đơn vị', platformAdmin: false, hint: 'Biểu mẫu, quy trình, phân quyền' },
  { subject: 'alice', username: 'Alice (chuyên viên)', platformAdmin: false, hint: 'Lập và nộp hồ sơ' },
  { subject: 'bob', username: 'Bob (trưởng phòng)', platformAdmin: false, hint: 'Duyệt cấp 1' },
  { subject: 'carol', username: 'Carol (giám đốc)', platformAdmin: false, hint: 'Duyệt cấp 2' },
];

export default function Login() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = params.get('next') ?? '/select-tenant';
  const [subject, setSubject] = useState('');
  const [platformAdmin, setPlatformAdmin] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const signIn = async (input: { subject: string; username?: string; platformAdmin: boolean }) => {
    setBusy(true);
    setError(null);
    try {
      await devLogin(input);
      navigate(next.startsWith('/app') ? next : '/select-tenant', { replace: true });
    } catch (e) {
      setError(e instanceof TypeError ? 'Không kết nối được backend (localhost:8080).' : (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (subject.trim()) void signIn({ subject: subject.trim(), platformAdmin });
  };

  return (
    <div className="min-h-screen grid lg:grid-cols-[1.1fr_1fr] bg-[#f3efe6] text-[#14161c]">
      <section className="relative hidden lg:flex flex-col justify-between p-14 overflow-hidden border-r border-black/10">
        <Link to="/" className="flex items-center gap-2 text-sm font-semibold"><Icon name="arrow_back" className="text-[18px]" />Trang giới thiệu</Link>
        <div className="max-w-lg">
          <h1 className="text-5xl leading-[1.02] tracking-[-0.035em] font-semibold" style={{ fontFamily: 'Instrument Sans, sans-serif' }}>
            Hồ sơ đi đúng đường, đóng dấu đúng người.
          </h1>
          <p className="mt-5 text-[17px] text-black/65 leading-relaxed">
            Đăng nhập để lập hồ sơ, xử lý việc được giao hoặc cấu hình biểu mẫu và quy trình cho đơn vị của bạn.
          </p>
        </div>
        <div aria-hidden="true" className="absolute right-16 bottom-28 w-40 h-40 rounded-full border-[6px] border-[#c8361f]/80 text-[#c8361f]/85 flex items-center justify-center rotate-[-14deg]">
          <div className="w-[128px] h-[128px] rounded-full border-2 border-current flex flex-col items-center justify-center text-center">
            <span className="text-[11px] tracking-[0.2em] font-bold">DRWP</span>
            <span className="text-xl font-black leading-none mt-1">ĐÃ DUYỆT</span>
          </div>
        </div>
        <p className="text-xs text-black/50">Mỗi đơn vị một không gian dữ liệu riêng. Quyền được kiểm tra ở từng thao tác.</p>
      </section>

      <section className="flex items-center justify-center p-6 sm:p-12 bg-white">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-2 mb-10">
            <span className="w-9 h-9 rounded-lg bg-primary text-white flex items-center justify-center"><Icon name="hub" /></span>
            <span className="text-xl font-bold text-primary" style={{ fontFamily: 'Instrument Sans, sans-serif' }}>DRWP</span>
          </div>
          <h2 className="text-2xl font-semibold tracking-tight">Đăng nhập</h2>

          {config.authMode === 'oidc' ? (
            <div className="mt-6 space-y-4">
              <p className="text-sm text-on-surface-variant">Đăng nhập một lần qua máy chủ định danh của tổ chức (Keycloak).</p>
              <Button className="w-full h-11" onClick={() => void oidcLogin(next.startsWith('/app') ? next : '/select-tenant')}>
                <Icon name="login" className="text-[18px] mr-2" />Đăng nhập bằng Keycloak
              </Button>
            </div>
          ) : (
            <>
              <p className="mt-2 text-sm text-on-surface-variant">
                Chế độ phát triển: backend cấp token thử nghiệm qua <code className="text-xs">POST /dev/token</code>.
              </p>
              <ul className="mt-6 space-y-2" aria-label="Tài khoản mẫu">
                {presets.map((p) => (
                  <li key={p.subject}>
                    <button type="button" disabled={busy} onClick={() => void signIn(p)} data-testid={`login-${p.subject}`}
                      className="w-full flex items-center gap-3 rounded-lg border border-outline-variant/70 px-3.5 py-2.5 text-left hover:border-secondary hover:bg-surface-container-low transition-colors disabled:opacity-50">
                      <span className="w-8 h-8 rounded-full bg-surface-container text-primary text-xs font-bold flex items-center justify-center uppercase">{p.subject.slice(0, 2)}</span>
                      <span className="flex-1">
                        <span className="block text-sm font-medium">{p.username}</span>
                        <span className="block text-xs text-on-surface-variant">{p.hint}</span>
                      </span>
                      {p.platformAdmin ? <Icon name="shield_person" className="text-[18px] text-outline" /> : null}
                    </button>
                  </li>
                ))}
              </ul>
              <form onSubmit={submit} className="mt-6 pt-6 border-t border-outline-variant/50 space-y-3">
                <label htmlFor="subject" className="block text-[13px] font-medium">Hoặc nhập subject bất kỳ</label>
                <Input id="subject" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="ví dụ: nguyen.van.a" autoComplete="username" />
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={platformAdmin} onChange={(e) => setPlatformAdmin(e.target.checked)} />
                  Quản trị nền tảng (platform-admin)
                </label>
                <Button type="submit" className="w-full h-11" disabled={busy || !subject.trim()}>Đăng nhập</Button>
              </form>
            </>
          )}
          {error ? <p role="alert" className="mt-4 text-sm text-error">{error}</p> : null}
        </div>
      </section>
    </div>
  );
}
