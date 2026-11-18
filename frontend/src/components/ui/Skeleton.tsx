/**
 * Skeleton theo khối: giữ đúng kích thước khối thật để bố cục không nhảy khi dữ liệu về.
 * Chỉ dùng cho lần tải đầu (isPending); khi làm mới đã có dữ liệu thì giữ nội dung cũ và hiện thanh tiến trình.
 * Tôn trọng prefers-reduced-motion (index.css tắt animation).
 */
const bar = 'sk bg-surface-dim/70 rounded';

export function SkeletonLine({ className = 'h-4 w-32' }: { className?: string }) {
  return <div aria-hidden="true" className={`${bar} ${className}`} />;
}

const widths = ['w-24', 'w-40', 'w-32', 'w-20', 'w-28', 'w-36'];

export function TableSkeleton({ rows = 6, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div role="status" aria-label="Đang tải bảng" className="overflow-hidden">
      <div className="flex gap-6 bg-surface-container-low px-4 h-10 items-center border-b border-outline-variant/30">
        {Array.from({ length: cols }, (_, c) => <SkeletonLine key={c} className={`h-3 ${widths[c % widths.length]}`} />)}
      </div>
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex gap-6 px-4 h-14 items-center border-b border-outline-variant/10">
          {Array.from({ length: cols }, (_, c) => (
            <SkeletonLine key={c} className={`h-4 ${widths[(r + c) % widths.length]}`} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function StatCardsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div role="status" aria-label="Đang tải số liệu" className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="h-[118px] p-5 rounded-xl border border-outline-variant/40 bg-surface-container-lowest">
          <SkeletonLine className="h-3 w-24" />
          <SkeletonLine className="h-8 w-16 mt-4" />
          <SkeletonLine className="h-3 w-32 mt-3" />
        </div>
      ))}
    </div>
  );
}

export function ListSkeleton({ rows = 5, withAvatar = true }: { rows?: number; withAvatar?: boolean }) {
  return (
    <ul role="status" aria-label="Đang tải danh sách" className="divide-y divide-outline-variant/20">
      {Array.from({ length: rows }, (_, i) => (
        <li key={i} className="flex items-center gap-3 py-3 px-4 h-[68px]">
          {withAvatar ? <div aria-hidden="true" className={`${bar} w-9 h-9 rounded-full shrink-0`} /> : null}
          <div className="flex-1 space-y-2">
            <SkeletonLine className={`h-4 ${widths[i % widths.length]}`} />
            <SkeletonLine className="h-3 w-48" />
          </div>
          <SkeletonLine className="h-6 w-20 rounded-full" />
        </li>
      ))}
    </ul>
  );
}

export function FormSkeleton({ fields = 5 }: { fields?: number }) {
  return (
    <div role="status" aria-label="Đang tải biểu mẫu" className="space-y-5">
      {Array.from({ length: fields }, (_, i) => (
        <div key={i} className="space-y-2">
          <SkeletonLine className="h-3 w-28" />
          <SkeletonLine className={`w-full ${i % 3 === 2 ? 'h-24' : 'h-[38px]'}`} />
        </div>
      ))}
    </div>
  );
}

export function DetailSkeleton() {
  return (
    <div role="status" aria-label="Đang tải chi tiết" className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-2 rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-6 space-y-4">
        <SkeletonLine className="h-6 w-56" />
        <div className="grid grid-cols-2 gap-4">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="space-y-2">
              <SkeletonLine className="h-3 w-20" />
              <SkeletonLine className="h-5 w-36" />
            </div>
          ))}
        </div>
      </div>
      <div className="rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-6 space-y-3">
        <SkeletonLine className="h-5 w-32" />
        {Array.from({ length: 4 }, (_, i) => <SkeletonLine key={i} className="h-12 w-full" />)}
      </div>
    </div>
  );
}

export function CardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div role="status" aria-label="Đang tải" className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="h-[168px] rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-5 space-y-3">
          <SkeletonLine className="h-5 w-40" />
          <SkeletonLine className="h-3 w-full" />
          <SkeletonLine className="h-3 w-3/4" />
          <div className="flex gap-2 pt-3">
            <SkeletonLine className="h-6 w-20 rounded-full" />
            <SkeletonLine className="h-6 w-16 rounded-full" />
          </div>
        </div>
      ))}
    </div>
  );
}
