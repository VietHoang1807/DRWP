import { http } from '../lib/http';
import { useTenantMutation, useTenantQuery } from './client';
import type { Page, TaskDetail, TaskView, WorkflowDefinitionView, WorkflowSpec, WorkflowVersionView } from './types';

export const workflowApi = {
  definitions: () => http.get<WorkflowDefinitionView[]>('/api/workflow/definitions'),
  definition: (code: string) => http.get<WorkflowDefinitionView>(`/api/workflow/definitions/${code}`),
  versions: (code: string) => http.get<WorkflowVersionView[]>(`/api/workflow/definitions/${code}/versions`),
  create: (v: { code: string; name: string; description?: string; formCode: string; spec: WorkflowSpec }) =>
    http.post<WorkflowVersionView>('/api/workflow/definitions', v),
  update: (code: string, v: { name?: string; description?: string; status?: string }) =>
    http.patch<WorkflowDefinitionView>(`/api/workflow/definitions/${code}`, v),
  newDraft: (code: string, spec?: WorkflowSpec) =>
    http.post<WorkflowVersionView>(`/api/workflow/definitions/${code}/versions`, spec ? { spec } : undefined),
  updateDraft: (code: string, no: number, spec: WorkflowSpec) =>
    http.put<WorkflowVersionView>(`/api/workflow/definitions/${code}/versions/${no}`, { spec }),
  publish: (code: string, no: number) => http.post<WorkflowVersionView>(`/api/workflow/definitions/${code}/versions/${no}/publish`),
  deprecate: (code: string, no: number) =>
    http.post<WorkflowVersionView>(`/api/workflow/definitions/${code}/versions/${no}/deprecate`),
  inbox: (page: number, size: number) => http.get<Page<TaskView>>('/api/workflow/tasks', { page, size }),
  task: (id: string) => http.get<TaskDetail>(`/api/workflow/tasks/${id}`),
  transition: (id: string, code: string, comment?: string, expectedVersion?: number) =>
    http.post<TaskDetail>(`/api/workflow/tasks/${id}/transitions/${code}`, { comment, expectedVersion }),
  cancel: (instanceId: string, reason?: string) => http.post<unknown>(`/api/workflow/instances/${instanceId}/cancel`, { reason }),
};

export const useDefinitions = (enabled = true) => useTenantQuery(['workflow', 'definitions'], workflowApi.definitions, { enabled });
export const useWorkflowVersions = (code?: string) =>
  useTenantQuery(['workflow', 'definitions', code, 'versions'], () => workflowApi.versions(code!), { enabled: !!code });
export const useInbox = (page = 0, size = 20) =>
  useTenantQuery(['tasks', 'inbox', page, size], () => workflowApi.inbox(page, size), { refetchInterval: 30_000 });
export const useTask = (id?: string) => useTenantQuery(['tasks', id], () => workflowApi.task(id!), { enabled: !!id });

export const useWorkflowMutation = <V, R>(fn: (v: V) => Promise<R>) => useTenantMutation(fn, [['workflow']]);
export const useTaskMutation = <V, R>(fn: (v: V) => Promise<R>, onSuccess?: (r: R, v: V) => void) =>
  useTenantMutation(fn, [['tasks'], ['records'], ['notifications'], ['dashboard']], onSuccess);
