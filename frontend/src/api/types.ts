/** DTO khớp với backend DRWP (business-app). */

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type Action = 'VIEW' | 'CREATE' | 'UPDATE' | 'DELETE' | 'APPROVE' | 'REJECT' | 'EXPORT' | 'ASSIGN' | 'SHARE'
  | 'CONFIGURE' | 'AUDIT' | 'MANAGE';
export const ACTIONS: Action[] = ['VIEW', 'CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'EXPORT', 'ASSIGN', 'SHARE',
  'CONFIGURE', 'AUDIT', 'MANAGE'];

// ---- tenant
export interface TenantView {
  tenantId: string;
  displayName: string;
  status: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
  complianceProfiles: string[];
  clusterId: string | null;
  schemaName: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface MyTenant {
  tenantId: string;
  displayName: string;
}

// ---- identity
export interface Me {
  subject: string;
  username: string;
  tenant: string;
  roles: string[];
  permission?: { resource: string; mask: number; actions: Action[] };
}
export interface GrantView {
  resource: string;
  allow: Action[];
  deny: Action[];
  allowMask: number;
  denyMask: number;
}
export interface RoleView {
  code: string;
  name: string;
  description: string | null;
  system: boolean;
  permissions: GrantView[];
}
export interface MemberView {
  subject: string;
  displayName: string | null;
  status: string;
  roles: string[];
}

// ---- metadata
export type FieldType = 'TEXT' | 'TEXTAREA' | 'EMAIL' | 'NUMBER' | 'INTEGER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'SELECT'
  | 'MULTISELECT' | 'FILE' | 'USER';
export interface FieldDef {
  key: string;
  label?: string;
  type: FieldType;
  required?: boolean;
  requiredIf?: unknown;
  options?: string[];
  minLength?: number;
  maxLength?: number;
  min?: number;
  max?: number;
  pattern?: string;
  filterable?: boolean;
  sensitive?: boolean;
  description?: string;
}
export interface FormSchema {
  fields: FieldDef[];
}
export interface FormView {
  code: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  latestPublishedVersion: number | null;
  draftVersion: number | null;
}
export interface FormVersionView {
  id: string;
  formCode: string;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  schema: FormSchema;
  checksum: string | null;
  createdBy: string;
  createdAt: string;
  publishedBy: string | null;
  publishedAt: string | null;
}
export interface Violation {
  field: string;
  code: string;
  message: string;
}

// ---- record
export type RecordStatus = 'DRAFT' | 'SUBMITTED' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'RETURNED' | 'CANCELLED';
export const RECORD_STATUSES: RecordStatus[] = ['DRAFT', 'SUBMITTED', 'IN_PROGRESS', 'APPROVED', 'REJECTED', 'RETURNED', 'CANCELLED'];
export interface RecordView {
  id: string;
  recordCode: string;
  formCode: string;
  formVersionId: string;
  formVersionNo: number;
  status: RecordStatus;
  data: Record<string, unknown>;
  owner: string;
  workflowInstanceId: string | null;
  createdAt: string;
  submittedAt: string | null;
  completedAt: string | null;
  version: number;
}
export interface Revision {
  revisionNo: number;
  changeType: string;
  status: string;
  data: Record<string, unknown>;
  changedBy: string;
  changedAt: string;
}

// ---- workflow
export interface WorkflowDefinitionView {
  code: string;
  name: string;
  description: string | null;
  formCode: string;
  status: 'ACTIVE' | 'ARCHIVED';
  latestPublishedVersion: number | null;
  draftVersion: number | null;
}
export interface StateSpec {
  code: string;
  name?: string;
  type: 'TASK' | 'DECISION' | 'END';
  assignment?: { type: 'ROLE' | 'USER' | 'SUBMITTER' | 'FIELD'; value?: string };
  timeout?: string;
  onTimeout?: string;
  outcome?: 'APPROVED' | 'REJECTED' | 'RETURNED' | 'CANCELLED';
}
export interface TransitionSpec {
  code: string;
  from: string;
  to: string;
  label?: string;
  permission?: Action;
  guard?: unknown;
  commentRequired?: boolean;
}
export interface WorkflowSpec {
  initialState: string;
  states: StateSpec[];
  transitions: TransitionSpec[];
}
export interface WorkflowVersionView {
  id: string;
  definitionCode: string;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  spec: WorkflowSpec;
  checksum: string | null;
  createdBy: string;
  createdAt: string;
  publishedBy: string | null;
  publishedAt: string | null;
}
export interface TaskView {
  id: string;
  name: string;
  stateCode: string;
  status: 'OPEN' | 'COMPLETED' | 'CANCELLED';
  assigneeType: 'USER' | 'ROLE';
  assigneeValue: string;
  dueAt: string | null;
  createdAt: string;
  completedBy: string | null;
  completedAction: string | null;
  comment: string | null;
  instanceId: string;
  recordId: string;
  recordCode: string;
  definitionCode: string;
}
export interface HistoryRow {
  seq: number;
  taskId: string | null;
  fromState: string | null;
  toState: string;
  transitionCode: string | null;
  automatic: boolean;
  actor: string;
  comment: string | null;
  occurredAt: string;
}
export interface TransitionOption {
  code: string;
  label: string;
  to: string;
  permission: Action;
  commentRequired: boolean;
  permitted: boolean;
}
export interface TaskDetail {
  task: TaskView;
  instanceVersion: number;
  recordData: Record<string, unknown>;
  recordStatus: RecordStatus;
  assignedToMe: boolean;
  transitions: TransitionOption[];
  history: HistoryRow[];
}
export interface InstanceView {
  id: string;
  definitionCode: string;
  versionNo: number;
  recordId: string;
  recordCode: string;
  recordOwner: string;
  status: 'RUNNING' | 'COMPLETED' | 'CANCELLED';
  currentState: string;
  outcome: string | null;
  startedBy: string;
  startedAt: string;
  endedAt: string | null;
  version: number;
  tasks: TaskView[];
  history: HistoryRow[];
}

// ---- file
export interface FileView {
  id: string;
  recordId: string | null;
  name: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: string;
  uploadedBy: string;
  uploadedAt: string;
}

// ---- notification
export interface NotificationView {
  id: string;
  type: string;
  title: string;
  body: string | null;
  refType: string | null;
  refId: string | null;
  read: boolean;
  createdAt: string;
}

// ---- audit
export interface AuditRow {
  id: number;
  occurredAt: string;
  actor: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  outcome: 'SUCCESS' | 'DENIED' | 'FAILED';
  correlationId: string | null;
  detail: Record<string, unknown>;
}
