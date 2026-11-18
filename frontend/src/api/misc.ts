import { http, request } from '../lib/http';
import { useTenantMutation, useTenantQuery } from './client';
import type { AuditRow, FileView, NotificationView, Page } from './types';

export const notificationsApi = {
  list: (unreadOnly: boolean, page: number, size: number) =>
    http.get<Page<NotificationView>>('/api/notifications', { unreadOnly, page, size }),
  unread: () => http.get<{ unread: number }>('/api/notifications/unread-count'),
  read: (id: string) => http.post<void>(`/api/notifications/${id}/read`),
  readAll: () => http.post<{ updated: number }>('/api/notifications/read-all'),
};

export const useNotifications = (unreadOnly: boolean, page = 0, size = 20) =>
  useTenantQuery(['notifications', unreadOnly, page, size], () => notificationsApi.list(unreadOnly, page, size));
export const useUnreadCount = () =>
  useTenantQuery(['notifications', 'unread'], notificationsApi.unread, { refetchInterval: 30_000 });
export const useNotificationMutation = <V, R>(fn: (v: V) => Promise<R>) => useTenantMutation(fn, [['notifications']]);

export interface AuditFilter {
  resourceType?: string;
  resourceId?: string;
  actor?: string;
  action?: string;
  outcome?: string;
  page?: number;
  size?: number;
}
export const auditApi = {
  events: (f: AuditFilter) => http.get<Page<AuditRow>>('/api/audit/events', { ...f }),
};
export const useAudit = (f: AuditFilter, enabled = true) =>
  useTenantQuery(['audit', f], () => auditApi.events(f), { enabled, placeholderData: (prev) => prev });

export const filesApi = {
  upload(file: File, recordId?: string) {
    const body = new FormData();
    body.append('file', file);
    return request<FileView>(`/api/files${recordId ? `?recordId=${recordId}` : ''}`, { method: 'POST', body });
  },
  /** Tải về qua API (có header Authorization) rồi mở bằng object URL; không dùng link công khai. */
  async download(file: FileView) {
    const res = await request<Response>(`/api/files/${file.id}/content`, { raw: true });
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = file.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  },
  remove: (id: string) => http.del<void>(`/api/files/${id}`),
};
