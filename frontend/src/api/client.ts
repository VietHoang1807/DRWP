import { useMutation, useQuery, useQueryClient, type UseQueryOptions } from '@tanstack/react-query';
import { useTenantId } from '../auth/session';

/**
 * Khóa cache luôn chứa tenant: đổi tenant không thể đọc nhầm cache tenant khác
 * (tài liệu 02 mục 3 "cache leakage").
 */
export function useTenantQuery<T>(key: readonly unknown[], fn: () => Promise<T>,
  options: Omit<UseQueryOptions<T>, 'queryKey' | 'queryFn'> = {}) {
  const tenant = useTenantId();
  return useQuery<T>({ queryKey: ['t', tenant, ...key], queryFn: fn, enabled: !!tenant && options.enabled !== false, ...options });
}

/** Mutation làm mới các khóa liên quan của tenant hiện tại khi thành công. */
export function useTenantMutation<V, R>(fn: (vars: V) => Promise<R>, invalidate: readonly unknown[][] = [],
  onSuccess?: (result: R, vars: V) => void) {
  const qc = useQueryClient();
  const tenant = useTenantId();
  return useMutation<R, Error, V>({
    mutationFn: fn,
    onSuccess: async (result, vars) => {
      await Promise.all(invalidate.map((k) => qc.invalidateQueries({ queryKey: ['t', tenant, ...k] })));
      onSuccess?.(result, vars);
    },
  });
}
