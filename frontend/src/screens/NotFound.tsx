import { Link } from 'react-router';

export default function NotFound() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-surface p-6">
      <div className="text-center">
        <p className="text-7xl font-bold text-primary" style={{ fontFamily: 'Instrument Sans, Inter, sans-serif' }}>404</p>
        <p className="mt-3 text-on-surface-variant">Không tìm thấy trang bạn cần.</p>
        <Link to="/app" className="inline-block mt-6 text-secondary font-medium">Về trang tổng quan</Link>
      </div>
    </main>
  );
}
