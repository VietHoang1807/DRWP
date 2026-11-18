import { useEffect, useRef, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { Badge } from './Badge';
import { t, label } from '../../lib/i18n';

export function Icon({ name, className = 'text-[20px]' }: { name: string; className?: string }) {
  return <span aria-hidden="true" className={`material-symbols-outlined ${className}`}>{name}</span>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-on-surface">{title}</h1>
        {subtitle ? <p className="mt-1 text-sm text-on-surface-variant">{subtitle}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function Panel({ title, actions, children, className = '', padded = true }:
  { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string; padded?: boolean }) {
  return (
    <section className={`rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-[0_1px_3px_rgba(15,23,42,0.05)] ${className}`}>
      {title || actions ? (
        <header className="flex items-center justify-between gap-3 px-5 py-3.5 border-b border-outline-variant/30">
          <h2 className="text-sm font-semibold text-on-surface">{title}</h2>
          {actions}
        </header>
      ) : null}
      <div className={padded ? 'p-5' : ''}>{children}</div>
    </section>
  );
}

const statusVariant: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  DRAFT: 'default', SUBMITTED: 'info', IN_PROGRESS: 'warning', APPROVED: 'success', REJECTED: 'error', RETURNED: 'warning',
  CANCELLED: 'default', PUBLISHED: 'success', DEPRECATED: 'default', ACTIVE: 'success', SUSPENDED: 'warning',
  PROVISIONING: 'info', ARCHIVED: 'default', OPEN: 'warning', COMPLETED: 'success', RUNNING: 'info', SUCCESS: 'success',
  DENIED: 'error', FAILED: 'error',
};

export function StatusBadge({ status, map = t.recordStatus }: { status?: string | null; map?: Record<string, string> }) {
  return <Badge variant={statusVariant[status ?? ''] ?? 'default'}>{label(map, status)}</Badge>;
}

export function Field({ label: text, required, error, hint, children, htmlFor }:
  { label: string; required?: boolean; error?: string; hint?: string; children: ReactNode; htmlFor?: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-[13px] font-medium text-on-surface">
        {text}
        {required ? <span className="text-error"> *</span> : null}
      </label>
      {children}
      {error ? <p className="text-xs text-error">{error}</p> : hint ? <p className="text-xs text-on-surface-variant">{hint}</p> : null}
    </div>
  );
}

const control = 'w-full rounded border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface focus:outline-none focus:border-secondary focus:ring-2 focus:ring-secondary/15 disabled:opacity-60';

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${control} h-[38px] ${props.className ?? ''}`} />;
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`${control} py-2 min-h-24 ${props.className ?? ''}`} />;
}

export function EmptyState({ icon = 'inbox', title, description, action }:
  { icon?: string; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-14 px-6">
      <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline">
        <Icon name={icon} className="text-[24px]" />
      </div>
      <p className="mt-4 font-medium text-on-surface">{title}</p>
      {description ? <p className="mt-1 text-sm text-on-surface-variant max-w-sm">{description}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border border-error/30 bg-error-container/40 p-4 text-sm text-on-error-container">
      <Icon name="error" />
      <div className="flex-1">{message}</div>
      {onRetry ? <button type="button" onClick={onRetry} className="font-medium underline">Thử lại</button> : null}
    </div>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'success' | 'warning'; children: ReactNode }) {
  const tones = {
    info: 'border-secondary/30 bg-secondary-fixed/40 text-on-secondary-fixed-variant',
    success: 'border-[#A7F3D0] bg-[#ECFDF5] text-[#065F46]',
    warning: 'border-[#FDE68A] bg-[#FFFBEB] text-[#92400E]',
  };
  return <div className={`rounded-lg border p-3 text-sm ${tones[tone]}`}>{children}</div>;
}

/** Hộp thoại dùng thẻ <dialog> gốc (focus trap, Esc đóng). */
export function Modal({ open, title, onClose, children, footer, wide }:
  { open: boolean; title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} onClose={onClose}
      className={`m-auto rounded-xl p-0 backdrop:bg-on-surface/40 shadow-2xl w-[calc(100%-2rem)] ${wide ? 'max-w-3xl' : 'max-w-lg'}`}>
      {open ? (
        <div className="flex flex-col max-h-[85vh]">
          <header className="flex items-center justify-between px-5 py-4 border-b border-outline-variant/40">
            <h2 className="font-semibold text-on-surface">{title}</h2>
            <button type="button" aria-label="Đóng" onClick={onClose} className="w-8 h-8 rounded hover:bg-surface-container-low">
              <Icon name="close" />
            </button>
          </header>
          <div className="p-5 overflow-y-auto">{children}</div>
          {footer ? <footer className="flex justify-end gap-2 px-5 py-3 border-t border-outline-variant/40 bg-surface-container-low">{footer}</footer> : null}
        </div>
      ) : null}
    </dialog>
  );
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-outline-variant/30 text-sm text-on-surface-variant">
      <span>{total === 0 ? 'Không có kết quả' : `${page * size + 1}–${Math.min(total, (page + 1) * size)} / ${total}`}</span>
      <div className="flex gap-1">
        <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}
          className="w-8 h-8 rounded border border-outline-variant/60 disabled:opacity-40" aria-label="Trang trước">
          <Icon name="chevron_left" className="text-[18px]" />
        </button>
        <span className="px-2 leading-8">{page + 1}/{pages}</span>
        <button type="button" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}
          className="w-8 h-8 rounded border border-outline-variant/60 disabled:opacity-40" aria-label="Trang sau">
          <Icon name="chevron_right" className="text-[18px]" />
        </button>
      </div>
    </div>
  );
}

export function KeyValue({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
      {items.map(([k, v]) => (
        <div key={k}>
          <dt className="text-xs text-on-surface-variant">{k}</dt>
          <dd className="mt-0.5 text-sm text-on-surface break-words">{v}</dd>
        </div>
      ))}
    </dl>
  );
}
