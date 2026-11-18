import { http } from '../lib/http';
import { useTenantMutation, useTenantQuery } from './client';
import type { FileView, InstanceView, Page, RecordStatus, RecordView, Revision } from './types';

export interface RecordFilter {
  formCode?: string;
  status?: RecordStatus;
  createdBy?: string;
  recordCode?: string;
  sort?: string;
  desc?: boolean;
  page?: number;
  size?: number;
  data?: Record<string, string>;
}

function query(f: RecordFilter) {
  const q: Record<string, string | number | boolean | undefined> = {
    formCode: f.formCode, status: f.status, createdBy: f.createdBy, recordCode: f.recordCode,
    sort: f.sort, desc: f.desc, page: f.page, size: f.size,
  };
  for (const [k, v] of Object.entries(f.data ?? {})) {
    if (v) q[`data.${k}`] = v;
  }
  return q;
}

export const recordsApi = {
  search: (f: RecordFilter) => http.get<Page<RecordView>>('/api/records', query(f)),
  get: (id: string) => http.get<RecordView>(`/api/records/${id}`),
  create: (v: { formCode: string; data: Record<string, unknown>; submit: boolean }) => http.post<RecordView>('/api/records', v),
  update: (id: string, data: Record<string, unknown>, expectedVersion: number) =>
    http.put<RecordView>(`/api/records/${id}`, { data, expectedVersion }),
  submit: (id: string, expectedVersion?: number) => http.post<RecordView>(`/api/records/${id}/submit`, { expectedVersion }),
  cancel: (id: string) => http.post<RecordView>(`/api/records/${id}/cancel`),
  revisions: (id: string) => http.get<Revision[]>(`/api/records/${id}/revisions`),
  instances: (recordId: string) => http.get<InstanceView[]>('/api/workflow/instances', { recordId }),
  files: (recordId: string) => http.get<FileView[]>('/api/files', { recordId }),
};

export const useRecords = (f: RecordFilter, enabled = true) =>
  useTenantQuery(['records', 'search', f], () => recordsApi.search(f), { enabled, placeholderData: (prev) => prev });
// Hồ sơ vừa nộp: quy trình được khởi tạo bất đồng bộ (ADR 016) nên thăm dò ngắn cho tới khi rời SUBMITTED
export const useRecord = (id?: string) => useTenantQuery(['records', id], () => recordsApi.get(id!), {
  enabled: !!id,
  refetchInterval: (q) => (q.state.data?.status === 'SUBMITTED' ? 1500 : false),
});
export const useRevisions = (id?: string) =>
  useTenantQuery(['records', id, 'revisions'], () => recordsApi.revisions(id!), { enabled: !!id });
export const useRecordInstances = (id?: string) =>
  useTenantQuery(['records', id, 'instances'], () => recordsApi.instances(id!), { enabled: !!id });
export const useRecordFiles = (id?: string) =>
  useTenantQuery(['records', id, 'files'], () => recordsApi.files(id!), { enabled: !!id });

export const useRecordMutation = <V, R>(fn: (v: V) => Promise<R>, onSuccess?: (r: R, v: V) => void) =>
  useTenantMutation(fn, [['records'], ['tasks'], ['dashboard']], onSuccess);
