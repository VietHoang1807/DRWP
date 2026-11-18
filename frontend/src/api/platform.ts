import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../lib/http';
import type { MyTenant, TenantView } from './types';

export interface CreateTenantInput {
  tenantId: string;
  displayName: string;
  clusterId?: string;
  complianceProfiles?: string[];
  adminSubject: string;
  adminDisplayName?: string;
}

export const platformApi = {
  myTenants: () => http.get<MyTenant[]>('/api/me/tenants', undefined, false),
  tenants: () => http.get<TenantView[]>('/api/platform/tenants', undefined, false),
  create: (input: CreateTenantInput) => http.post<TenantView>('/api/platform/tenants', input, false),
  action: (id: string, action: 'suspend' | 'activate' | 'migrate') =>
    http.post<TenantView | undefined>(`/api/platform/tenants/${encodeURIComponent(id)}/${action}`, undefined, false),
};

export function useMyTenants(enabled = true) {
  return useQuery({ queryKey: ['me', 'tenants'], queryFn: platformApi.myTenants, enabled });
}

export function usePlatformTenants() {
  return useQuery({ queryKey: ['platform', 'tenants'], queryFn: platformApi.tenants });
}

export function usePlatformMutation<V>(fn: (v: V) => Promise<unknown>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => Promise.all([
      qc.invalidateQueries({ queryKey: ['platform', 'tenants'] }),
      qc.invalidateQueries({ queryKey: ['me', 'tenants'] }),
    ]),
  });
}
