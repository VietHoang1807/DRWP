import { http } from '../lib/http';
import { useTenantMutation, useTenantQuery } from './client';
import type { FormSchema, FormVersionView, FormView, Violation } from './types';

export const metadataApi = {
  forms: () => http.get<FormView[]>('/api/metadata/forms'),
  form: (code: string) => http.get<FormView>(`/api/metadata/forms/${code}`),
  published: (code: string) => http.get<FormVersionView>(`/api/metadata/forms/${code}/published`),
  versions: (code: string) => http.get<FormVersionView[]>(`/api/metadata/forms/${code}/versions`),
  version: (code: string, no: number) => http.get<FormVersionView>(`/api/metadata/forms/${code}/versions/${no}`),
  create: (v: { code: string; name: string; description?: string; schema: FormSchema }) =>
    http.post<FormVersionView>('/api/metadata/forms', v),
  update: (code: string, v: { name?: string; description?: string; status?: string }) =>
    http.patch<FormView>(`/api/metadata/forms/${code}`, v),
  newDraft: (code: string, schema?: FormSchema) =>
    http.post<FormVersionView>(`/api/metadata/forms/${code}/versions`, schema ? { schema } : undefined),
  updateDraft: (code: string, no: number, schema: FormSchema) =>
    http.put<FormVersionView>(`/api/metadata/forms/${code}/versions/${no}`, { schema }),
  deleteDraft: (code: string, no: number) => http.del<void>(`/api/metadata/forms/${code}/versions/${no}`),
  publish: (code: string, no: number) => http.post<FormVersionView>(`/api/metadata/forms/${code}/versions/${no}/publish`),
  deprecate: (code: string, no: number) => http.post<FormVersionView>(`/api/metadata/forms/${code}/versions/${no}/deprecate`),
  validate: (code: string, no: number, data: Record<string, unknown>, full: boolean) =>
    http.post<{ valid: boolean; violations: Violation[] }>(`/api/metadata/forms/${code}/versions/${no}/validate`, { data, full }),
};

export const useForms = () => useTenantQuery(['forms'], metadataApi.forms);
export const usePublishedForm = (code?: string) =>
  useTenantQuery(['forms', code, 'published'], () => metadataApi.published(code!), { enabled: !!code, retry: false });
export const useFormVersions = (code?: string) =>
  useTenantQuery(['forms', code, 'versions'], () => metadataApi.versions(code!), { enabled: !!code });

export const useFormMutation = <V, R>(fn: (v: V) => Promise<R>) => useTenantMutation(fn, [['forms']]);

export const useFormVersion = (code?: string, no?: number) =>
  useTenantQuery(['forms', code, 'version', no], () => metadataApi.version(code!, no!), {
    enabled: !!code && !!no, staleTime: Infinity, // phiên bản đã phát hành là bất biến
  });
