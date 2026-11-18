const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
const date = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' });
const relative = new Intl.RelativeTimeFormat('vi-VN', { numeric: 'auto' });
const number = new Intl.NumberFormat('vi-VN');

export const fmt = {
  dateTime: (v?: string | null) => (v ? dateTime.format(new Date(v)) : '—'),
  date: (v?: string | null) => (v ? date.format(new Date(v)) : '—'),
  number: (v: number) => number.format(v),
  bytes(v: number) {
    if (v < 1024) return `${v} B`;
    if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`;
    return `${(v / 1024 / 1024).toFixed(1)} MB`;
  },
  relative(v?: string | null) {
    if (!v) return '—';
    const diff = (new Date(v).getTime() - Date.now()) / 1000;
    const abs = Math.abs(diff);
    if (abs < 60) return relative.format(Math.round(diff), 'second');
    if (abs < 3600) return relative.format(Math.round(diff / 60), 'minute');
    if (abs < 86400) return relative.format(Math.round(diff / 3600), 'hour');
    return relative.format(Math.round(diff / 86400), 'day');
  },
};
