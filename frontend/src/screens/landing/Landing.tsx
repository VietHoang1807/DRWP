import { useEffect, useRef } from 'react';
import { Link } from 'react-router';
import '../../vendor/scrollcraft/scrollcraft.css';
import './landing.css';
import type { ScrollCraftInstance } from '../../vendor/scrollcraft/scrollcraft.js';

/* Nội dung tĩnh hoist ra ngoài component (rendering-hoist-jsx). Số liệu đều là số thật của sản phẩm. */
const FIELDS = [
  { label: 'Họ và tên', value: 'Nguyễn Thị An', type: 'Văn bản' },
  { label: 'Loại nghỉ', value: 'Nghỉ phép năm', type: 'Chọn một' },
  { label: 'Từ ngày', value: '02/12/2026', type: 'Ngày' },
  { label: 'Số ngày', value: '7', type: 'Số nguyên' },
  { label: 'Lý do', value: 'Về quê dự đám cưới em gái', type: 'Văn bản dài' },
  { label: 'Đơn có xác nhận', value: 'don-nghi-phep.pdf', type: 'Tệp đính kèm' },
];

const STEPS = [
  { who: 'Alice', role: 'Chuyên viên', title: 'Nộp hồ sơ', note: 'Kiểm tra dữ liệu theo phiên bản biểu mẫu đang dùng.' },
  { who: 'Bob', role: 'Trưởng phòng', title: 'Duyệt cấp 1', note: 'Quá 3 ngày chưa xử lý thì tự chuyển lên.' },
  { who: 'Hệ thống', role: 'Rẽ nhánh', title: 'Trên 5 ngày?', note: 'Có: lên giám đốc. Không: kết thúc, đã duyệt.', fork: true },
  { who: 'Carol', role: 'Giám đốc', title: 'Duyệt cấp 2', note: 'Ý kiến bắt buộc khi từ chối hoặc trả lại.' },
];

const PROMISES = [
  ['Mỗi đơn vị một schema riêng', 'Dữ liệu của sở này không bao giờ nằm chung bảng với sở khác. Đổi đơn vị là đổi cả bộ nhớ đệm.'],
  ['Cấm luôn thắng cho phép', 'Một người nhiều vai trò vẫn bị chặn nếu một vai trò cấm. Kiểm tra ở máy chủ, không chỉ ẩn nút.'],
  ['Nhật ký không sửa được', 'Mỗi lần nộp, duyệt, từ chối, và mỗi lần bị từ chối truy cập đều được ghi lại kèm mã tương quan.'],
];

function Dossier({ code, small }: { code: string; small?: boolean }) {
  return (
    <div className={`ld-dossier${small ? ' ld-dossier--sm' : ''}`} aria-hidden="true">
      <span className="ld-dossier__tab">HỒ SƠ</span>
      <span className="ld-dossier__code">{code}</span>
      <span className="ld-dossier__title">Đơn xin nghỉ phép</span>
      <span className="ld-dossier__lines"><i /><i /><i /></span>
    </div>
  );
}

function Stamp({ className = '' }: { className?: string }) {
  return (
    <span className={`ld-stamp ${className}`} aria-hidden="true">
      <span>ĐÃ DUYỆT</span>
      <small>02.12.2026</small>
    </span>
  );
}

export default function Landing() {
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let api: ScrollCraftInstance | undefined;
    let cancelled = false;
    // Engine chỉ nạp ở route này (bundle-conditional), gỡ khi rời trang.
    void import('../../vendor/scrollcraft/scrollcraft.js').then(() => {
      if (!cancelled && root.current && window.ScrollCraft) api = window.ScrollCraft.mount(root.current);
    });
    return () => {
      cancelled = true;
      api?.destroy();
      document.documentElement.style.removeProperty('--sc-canvas');
    };
  }, []);

  return (
    <div ref={root} className="sc-root ld">
      <header className="ld-nav">
        <Link to="/" className="ld-logo">DRWP</Link>
        <nav aria-label="Trang giới thiệu" className="ld-nav__links">
          <a href="#bieu-mau">Biểu mẫu</a>
          <a href="#duong-duyet">Đường duyệt</a>
          <a href="#don-vi">Đơn vị</a>
          <Link to="/login" className="ld-nav__cta" data-testid="nav-login">Đăng nhập</Link>
        </nav>
      </header>

      <main>
        {/* ACT 1: hero nhiều lớp. Nền giấy, chữ, giao diện thật, bìa hồ sơ, lớp tiền cảnh. */}
        <section className="ld-hero" data-sc-act="pin" data-sc-span="1.35" aria-labelledby="hero-title">
          <div className="sc-stage ld-hero__stage">
            <div className="ld-plane ld-plane--paper" data-sc-parallax="-0.04" aria-hidden="true">
              <span className="ld-ghost">HỒ SƠ</span>
            </div>
            <div className="ld-hero__copy">
              <h1 id="hero-title" className="sc-display ld-hero__title">Hồ sơ đi đúng đường, đến đúng người.</h1>
              <p className="sc-lede ld-hero__lede">
                DRWP biến mỗi loại giấy tờ của đơn vị thành biểu mẫu số và quy trình phê duyệt có thể cấu hình. Không cần viết mã, không thất lạc.
              </p>
              <div className="ld-hero__actions">
                <Link to="/login" className="ld-btn ld-btn--accent" data-testid="hero-login">Đăng nhập</Link>
                <a href="#duong-duyet" className="ld-btn ld-btn--ghost">Xem một hồ sơ được duyệt</a>
              </div>
            </div>
            <div className="ld-plane ld-plane--ui" data-sc-parallax="-0.16" aria-hidden="true">
              <div className="ld-ui">
                <div className="ld-ui__bar"><b>Hồ sơ</b><span>Đơn vị: Sở Tài chính (demo)</span></div>
                <table>
                  <tbody>
                    <tr><td>LEAVE-2026-0012</td><td>Nghỉ phép năm</td><td><em className="is-run">Đang xử lý</em></td></tr>
                    <tr><td>LEAVE-2026-0011</td><td>Nghỉ ốm</td><td><em className="is-ok">Đã phê duyệt</em></td></tr>
                    <tr><td>LEAVE-2026-0010</td><td>Việc riêng</td><td><em className="is-back">Trả lại bổ sung</em></td></tr>
                    <tr><td>LEAVE-2026-0009</td><td>Nghỉ phép năm</td><td><em className="is-ok">Đã phê duyệt</em></td></tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div className="ld-plane ld-plane--subject" data-sc-parallax="-0.3" aria-hidden="true">
              <Dossier code="LEAVE-2026-0012" />
            </div>
            <div className="ld-plane ld-plane--fore" data-sc-parallax="-0.55" aria-hidden="true">
              <svg className="ld-clip" viewBox="0 0 40 110" width="40" height="110">
                <path d="M12 100V22a8 8 0 0 1 16 0v70a14 14 0 0 1-28 0V30" fill="none" stroke="#6f6c64" strokeWidth="3.2" strokeLinecap="round" />
              </svg>
              <Stamp className="ld-stamp--hero" />
            </div>
          </div>
        </section>

        {/* ACT 2: biểu mẫu tự dựng. Chữ căn phải, biểu mẫu bên trái. */}
        <section id="bieu-mau" className="ld-form" data-sc-act="pin" data-sc-span="2" aria-labelledby="form-title">
          <div className="sc-stage ld-form__stage">
            <div className="ld-sheet" aria-label="Ví dụ biểu mẫu Đơn xin nghỉ phép">
              <p className="ld-sheet__head"><span>Đơn xin nghỉ phép</span><span className="ld-mono">LEAVE · v3</span></p>
              {FIELDS.map((f, i) => (
                <div key={f.label} className="ld-row" data-sc-cue={(0.08 + i * 0.1).toFixed(2)}>
                  <span className="ld-row__label">{f.label}<small>{f.type}</small></span>
                  <span className="ld-row__value">{f.value}</span>
                </div>
              ))}
            </div>
            <div className="ld-form__copy">
              <h2 id="form-title" className="sc-display sc-display--lg">
                <span className="ld-line" data-sc-cue="0.02" data-sc-kinetic="words">Khai báo một lần.</span>{' '}
                <span className="ld-line" data-sc-cue="0.1" data-sc-kinetic="words">Biểu mẫu tự dựng.</span>
              </h2>
              <p className="sc-body" data-sc-cue="0.25">
                Người quản trị đặt tên trường, kiểu dữ liệu và ràng buộc. Máy chủ kiểm tra từng lần nộp, giữ phiên bản,
                và hồ sơ cũ vẫn đọc đúng theo phiên bản đã dùng khi nộp.
              </p>
              <p className="ld-figure" data-sc-cue="0.45">
                <span data-sc-count="0 12" data-sc-count-at="0.45 0.7">12</span>
                <span>kiểu trường, từ ngày giờ, chọn nhiều tới tệp đính kèm</span>
              </p>
            </div>
          </div>
        </section>

        {/* ACT 3: đỉnh. Đường duyệt trượt ngang dưới bìa hồ sơ đứng yên, dấu đóng ở bàn cuối. */}
        <section id="duong-duyet" className="ld-rail" data-sc-act="pan" data-sc-span="3.2" aria-labelledby="rail-title">
          <div className="sc-stage ld-rail__stage">
            <h2 id="rail-title" className="sc-display sc-display--md ld-rail__title">Đi qua từng bàn như hồ sơ giấy.<br />Chỉ là không ai làm mất nó.</h2>
            <div className="ld-rail__track" data-sc-pan="0">
              {STEPS.map((s, i) => (
                <article key={s.title} className={`ld-desk${s.fork ? ' ld-desk--fork' : ''}`}>
                  <p className="ld-desk__n ld-mono">Bàn {i + 1}</p>
                  <h3>{s.title}</h3>
                  <p className="ld-desk__who"><b>{s.who}</b> · {s.role}</p>
                  <p className="ld-desk__note">{s.note}</p>
                  {s.fork ? (
                    <svg className="ld-fork" viewBox="0 0 220 70" aria-hidden="true">
                      <path d="M0 35H70C100 35 100 8 130 8H220" fill="none" stroke="currentColor" strokeWidth="1.5" />
                      <path d="M70 35C100 35 100 62 130 62H220" fill="none" stroke="currentColor" strokeWidth="1.5" strokeDasharray="4 4" />
                      <text x="140" y="4" fontSize="10" fill="currentColor">trên 5 ngày</text>
                      <text x="140" y="56" fontSize="10" fill="currentColor">còn lại</text>
                    </svg>
                  ) : null}
                </article>
              ))}
              <article className="ld-desk ld-desk--end">
                <p className="ld-desk__n ld-mono">Kết thúc</p>
                <h3>Hồ sơ được trả về người nộp</h3>
                <p className="ld-desk__note">Kèm thông báo, lịch sử từng bước và ý kiến của từng người duyệt.</p>
                <Stamp className="ld-stamp--peak" />
              </article>
              <span className="ld-rail__tail" aria-hidden="true" />
            </div>
            <div className="ld-rail__dossier" aria-hidden="true"><Dossier code="LEAVE-2026-0012" small /></div>
            <div className="ld-rail__line" aria-hidden="true" />
          </div>
        </section>

        {/* ACT 4: nhiều đơn vị. Chia đôi, nền chuyển tối. */}
        <section id="don-vi" className="ld-tenant" data-sc-act="flow" data-sc-drift="#14161c" aria-labelledby="tenant-title">
          <div className="sc-wrap ld-tenant__grid">
            <div className="ld-tenant__lead" data-sc-in>
              <p className="sc-label ld-eyebrow">Nhiều đơn vị, một nền tảng</p>
              <h2 id="tenant-title" className="sc-display sc-display--lg">Mỗi sở, mỗi phòng có không gian riêng.</h2>
              <p className="ld-big"><span data-sc-count="0 12">12</span><span>quyền chuẩn cho mọi tài nguyên: xem, tạo, sửa, xoá, duyệt, từ chối, xuất, giao việc, chia sẻ, cấu hình, kiểm toán, quản trị.</span></p>
            </div>
            <ul className="ld-promises" data-sc-stagger="90">
              {PROMISES.map(([h, b]) => (
                <li key={h}><h3>{h}</h3><p>{b}</p></li>
              ))}
            </ul>
          </div>
        </section>

        {/* ACT 5: kết. Căn giữa, nền về lại màu giấy. */}
        <section className="ld-close" data-sc-act="flow" aria-labelledby="close-title">
          <div className="sc-wrap ld-close__inner" data-sc-in>
            <h2 id="close-title" className="sc-display sc-display--xl">Hồ sơ tiếp theo<br />không cần giấy.</h2>
            <p className="sc-lede">Đăng nhập bằng tài khoản đơn vị, chọn nơi làm việc và bắt đầu với các biểu mẫu đã có.</p>
            <Link to="/login" className="ld-btn ld-btn--accent ld-btn--lg" data-sc-magnet="0.25" data-testid="close-login">Đăng nhập</Link>
          </div>
          <footer className="sc-wrap ld-foot">
            <span>DRWP · Nền tảng Hồ sơ &amp; Quy trình động</span>
            <span>Modular monolith · Spring Boot · React</span>
          </footer>
        </section>
      </main>
    </div>
  );
}
