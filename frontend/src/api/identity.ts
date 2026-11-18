import { useQueries } from '@tanstack/react-query';
import { http } from '../lib/http';
import { useTenantId } from '../auth/session';
import { useTenantMutation, useTenantQuery } from './client';
import type { Action, Me, MemberView, RoleView } from './types';

export interface GrantInput {
  resource: string;
  allow: Action[];
  deny: Action[];
}

export const identityApi = {
  me: (resource?: string) => http.get<Me>('/api/identity/me', { resource }),
  roles: () => http.get<RoleView[]>('/api/identity/roles'),
  createRole: (v: { code: string; name: string; description?: string }) => http.post<RoleView>('/api/identity/roles', v),
  updateRole: (code: string, v: { name: string; description?: string }) => http.put<RoleView>(`/api/identity/roles/${code}`, v),
  deleteRole: (code: string) => http.del<void>(`/api/identity/roles/${code}`),
  setPermissions: (code: string, grants: GrantInput[]) => http.put<RoleView>(`/api/identity/roles/${code}/permissions`, grants),
  members: () => http.get<MemberView[]>('/api/identity/members'),
  addMember: (v: { subject: string; displayName?: string; roles: string[] }) => http.post<MemberView>('/api/identity/members', v),
  removeMember: (subject: string) => http.del<void>(`/api/identity/members/${encodeURIComponent(subject)}`),
  setMemberRoles: (subject: string, roles: string[]) =>
    http.put<MemberView>(`/api/identity/members/${encodeURIComponent(subject)}/roles`, { roles }),
};

export const useMe = () => useTenantQuery(['identity', 'me'], () => identityApi.me(), { staleTime: 60_000 });
export const useRoles = () => useTenantQuery(['identity', 'roles'], identityApi.roles);
export const useMembers = () => useTenantQuery(['identity', 'members'], identityApi.members);

/** Kiểm tra song song nhiều resource (một request mỗi resource, cache 60s). */
export function usePermissions(resources: string[]) {
  const tenant = useTenantId();
  const results = useQueries({
    queries: resources.map((r) => ({
      queryKey: ['t', tenant, 'identity', 'perm', r],
      queryFn: () => identityApi.me(r),
      enabled: !!tenant,
      staleTime: 60_000,
    })),
  });
  const can = (resource: string, action: Action) => {
    const i = resources.indexOf(resource);
    return !!results[i]?.data?.permission?.actions.includes(action);
  };
  return { can, loading: results.some((r) => r.isPending) };
}

export const useIdentityMutation = <V, R>(fn: (v: V) => Promise<R>) =>
  useTenantMutation(fn, [['identity']]);
