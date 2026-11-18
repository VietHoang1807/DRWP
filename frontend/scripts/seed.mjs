#!/usr/bin/env node
/**
 * Tạo dữ liệu demo cho DRWP (backend chạy profile `dev`, có POST /dev/token).
 *   npm run seed                       # mặc định http://localhost:8080
 *   DRWP_BACKEND_URL=http://host:8080 npm run seed
 * Chạy lại nhiều lần được: 409 (đã tồn tại) được bỏ qua; hồ sơ mẫu chỉ tạo khi alice chưa có hồ sơ nào.
 */
const BASE = process.env.DRWP_BACKEND_URL ?? 'http://localhost:8080';
const TENANT = process.env.DRWP_SEED_TENANT ?? 'demo';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, { token, tenant, body, ok = [] } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (tenant) headers['X-Tenant-Id'] = tenant;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let res;
  try {
    const init = { method, headers };
    if (body !== undefined) init.body = JSON.stringify(body);
    res = await fetch(BASE + path, init);
  } catch (e) {
    console.error(`\n✗ Không kết nối được ${BASE}. Backend đã chạy với profile dev chưa?\n  mvn -pl business-app spring-boot:run -Dspring-boot.run.profiles=dev`);
    throw e;
  }
  const text = await res.text();
  const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
  if (res.ok) return data;
  if (ok.includes(res.status)) return { __status: res.status, ...(typeof data === 'object' ? data : {}) };
  throw new Error(`${method} ${path} → ${res.status} ${typeof data === 'string' ? data : JSON.stringify(data)}`);
}

const token = async (subject, platformAdmin = false, username = subject) =>
  (await api('POST', '/dev/token', { body: { subject, username, platformAdmin } })).access_token;

const step = (msg) => console.log(`• ${msg}`);

async function waitTask(tok, recordId, tries = 40) {
  for (let i = 0; i < tries; i++) {
    const page = await api('GET', '/api/workflow/tasks?page=0&size=50', { token: tok, tenant: TENANT });
    const t = page.items.find((x) => x.recordId === recordId && x.status === 'OPEN');
    if (t) return t;
    await sleep(500);
  }
  throw new Error(`Không thấy công việc của hồ sơ ${recordId} (outbox/relay đã chạy chưa?)`);
}

const FORM_SCHEMA = {
  fields: [
    { key: 'fullName', label: 'Họ và tên', type: 'TEXT', required: true, maxLength: 120 },
    { key: 'leaveType', label: 'Loại nghỉ', type: 'SELECT', required: true, filterable: true,
      options: ['Nghỉ phép năm', 'Nghỉ ốm', 'Việc riêng'] },
    { key: 'fromDate', label: 'Từ ngày', type: 'DATE', required: true, filterable: true },
    { key: 'days', label: 'Số ngày', type: 'NUMBER', required: true, min: 0.5, max: 30 },
    { key: 'reason', label: 'Lý do', type: 'TEXTAREA', requiredIf: { field: 'days', op: 'gt', value: 3 },
      description: 'Bắt buộc khi nghỉ trên 3 ngày' },
    { key: 'attachments', label: 'Tệp đính kèm', type: 'FILE' },
  ],
};

const WORKFLOW_SPEC = {
  initialState: 'MANAGER_REVIEW',
  states: [
    { code: 'MANAGER_REVIEW', name: 'Trưởng phòng duyệt', type: 'TASK', assignment: { type: 'ROLE', value: 'MANAGER' } },
    { code: 'CHECK_DAYS', name: 'Kiểm tra số ngày', type: 'DECISION' },
    { code: 'DIRECTOR_REVIEW', name: 'Giám đốc duyệt', type: 'TASK', assignment: { type: 'ROLE', value: 'DIRECTOR' } },
    { code: 'APPROVED', name: 'Đã duyệt', type: 'END', outcome: 'APPROVED' },
    { code: 'REJECTED', name: 'Từ chối', type: 'END', outcome: 'REJECTED' },
    { code: 'RETURNED', name: 'Trả lại', type: 'END', outcome: 'RETURNED' },
  ],
  transitions: [
    { code: 'approve', from: 'MANAGER_REVIEW', to: 'CHECK_DAYS', permission: 'APPROVE', label: 'Đồng ý' },
    { code: 'reject', from: 'MANAGER_REVIEW', to: 'REJECTED', permission: 'REJECT', commentRequired: true, label: 'Từ chối' },
    { code: 'return', from: 'MANAGER_REVIEW', to: 'RETURNED', permission: 'REJECT', commentRequired: true, label: 'Trả lại bổ sung' },
    { code: 'long', from: 'CHECK_DAYS', to: 'DIRECTOR_REVIEW', guard: { field: 'days', op: 'gt', value: 5 }, label: 'Trên 5 ngày' },
    { code: 'short', from: 'CHECK_DAYS', to: 'APPROVED', label: 'Còn lại' },
    { code: 'approve', from: 'DIRECTOR_REVIEW', to: 'APPROVED', permission: 'APPROVE', label: 'Phê duyệt' },
    { code: 'reject', from: 'DIRECTOR_REVIEW', to: 'REJECTED', permission: 'REJECT', commentRequired: true, label: 'Từ chối' },
  ],
};

const ROLES = [
  { code: 'EMPLOYEE', name: 'Chuyên viên', grants: [{ resource: 'record:LEAVE', allow: ['CREATE'], deny: [] }] },
  { code: 'MANAGER', name: 'Trưởng phòng', grants: [
    { resource: 'wf:LEAVE_APPROVAL', allow: ['VIEW', 'APPROVE', 'REJECT'], deny: [] },
    { resource: 'record:LEAVE', allow: ['VIEW', 'CREATE'], deny: [] },
  ] },
  { code: 'DIRECTOR', name: 'Giám đốc', grants: [
    { resource: 'wf:LEAVE_APPROVAL', allow: ['VIEW', 'APPROVE', 'REJECT'], deny: [] },
    { resource: 'record:*', allow: ['VIEW'], deny: [] },
    { resource: 'audit', allow: ['AUDIT'], deny: [] },
  ] },
];
const MEMBERS = [
  { subject: 'alice', displayName: 'Alice (chuyên viên)', roles: ['EMPLOYEE'] },
  { subject: 'bob', displayName: 'Bob (trưởng phòng)', roles: ['MANAGER'] },
  { subject: 'carol', displayName: 'Carol (giám đốc)', roles: ['DIRECTOR'] },
];

async function main() {
  console.log(`DRWP seed → ${BASE}, đơn vị "${TENANT}"`);
  const root = await token('platform-root', true, 'Quản trị nền tảng');

  step('Tạo đơn vị');
  const t = await api('POST', '/api/platform/tenants', { token: root, ok: [409], body: {
    tenantId: TENANT, displayName: 'Sở Tài chính (demo)', clusterId: 'cluster-a',
    adminSubject: 'admin-demo', adminDisplayName: 'Quản trị đơn vị demo', complianceProfiles: [],
  } });
  console.log(t.__status === 409 ? '  đã tồn tại' : `  ${t.status}`);

  const admin = await token('admin-demo', false, 'Quản trị đơn vị');
  const as = { token: admin, tenant: TENANT };

  step('Vai trò và phân quyền');
  for (const r of ROLES) {
    await api('POST', '/api/identity/roles', { ...as, ok: [409], body: { code: r.code, name: r.name } });
    await api('PUT', `/api/identity/roles/${r.code}/permissions`, { ...as, body: r.grants });
  }

  step('Thành viên alice, bob, carol');
  for (const m of MEMBERS) {
    const res = await api('POST', '/api/identity/members', { ...as, ok: [409], body: m });
    if (res.__status === 409) await api('PUT', `/api/identity/members/${m.subject}/roles`, { ...as, body: { roles: m.roles } });
  }

  step('Biểu mẫu LEAVE');
  const form = await api('POST', '/api/metadata/forms', { ...as, ok: [409], body: { code: 'LEAVE', name: 'Đơn xin nghỉ phép', description: 'Mẫu demo', schema: FORM_SCHEMA } });
  if (form.__status !== 409) await api('POST', '/api/metadata/forms/LEAVE/versions/1/publish', as);

  step('Quy trình LEAVE_APPROVAL');
  const wf = await api('POST', '/api/workflow/definitions', { ...as, ok: [409], body: { code: 'LEAVE_APPROVAL', name: 'Duyệt nghỉ phép 2 cấp', formCode: 'LEAVE', spec: WORKFLOW_SPEC } });
  if (wf.__status !== 409) await api('POST', '/api/workflow/definitions/LEAVE_APPROVAL/versions/1/publish', as);

  const [alice, bob] = await Promise.all([token('alice', false, 'Alice'), token('bob', false, 'Bob')]);
  const asAlice = { token: alice, tenant: TENANT };
  const mine = await api('GET', '/api/records?createdBy=alice&size=1', asAlice);
  if (mine.total > 0) {
    step(`alice đã có ${mine.total} hồ sơ, bỏ qua tạo hồ sơ mẫu`);
  } else {
    step('Hồ sơ mẫu');
    const mk = (data, submit = true) => api('POST', '/api/records', { ...asAlice, body: { formCode: 'LEAVE', submit, data: { fullName: 'Alice Nguyễn', ...data } } });
    const long = await mk({ leaveType: 'Nghỉ phép năm', fromDate: '2026-12-02', days: 7, reason: 'Về quê dự đám cưới em gái' });
    const short = await mk({ leaveType: 'Nghỉ ốm', fromDate: '2026-11-20', days: 2 });
    const toReturn = await mk({ leaveType: 'Việc riêng', fromDate: '2026-11-25', days: 4, reason: 'Việc gia đình' });
    await mk({ leaveType: 'Nghỉ phép năm', fromDate: '2027-01-05', days: 1 }, false);
    await mk({ leaveType: 'Nghỉ ốm', fromDate: '2026-11-28', days: 1 });

    const asBob = { token: bob, tenant: TENANT };
    const t1 = await waitTask(bob, long.id);
    await api('POST', `/api/workflow/tasks/${t1.id}/transitions/approve`, { ...asBob, body: { comment: 'Đồng ý, chuyển giám đốc' } });
    const t2 = await waitTask(bob, short.id);
    await api('POST', `/api/workflow/tasks/${t2.id}/transitions/approve`, { ...asBob, body: { comment: 'Đồng ý' } });
    const t3 = await waitTask(bob, toReturn.id);
    await api('POST', `/api/workflow/tasks/${t3.id}/transitions/return`, { ...asBob, body: { comment: 'Bổ sung đơn có xác nhận của trưởng nhóm' } });
    console.log('  7 ngày: chờ giám đốc (carol) · 2 ngày: đã duyệt · 4 ngày: trả lại · 1 nháp · 1 chờ trưởng phòng (bob)');
  }

  console.log('\n✓ Xong. Mở http://localhost:5173/login và chọn tài khoản mẫu (admin-demo, alice, bob, carol).');
}

main().catch((e) => { console.error(`\n✗ ${e.message}`); process.exit(1); });
