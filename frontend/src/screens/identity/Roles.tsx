import { useEffect, useMemo, useState } from 'react';
import { identityApi, useIdentityMutation, usePermissions, useRoles, type GrantInput } from '../../api/identity';
import { useForms } from '../../api/metadata';
import { useDefinitions } from '../../api/workflow';
import { ACTIONS, type Action, type RoleView } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Modal, Notice, PageHeader, Panel, Textarea } from '../../components/ui/Primitives';
import { ListSkeleton, TableSkeleton } from '../../components/ui/Skeleton';
import { t, label } from '../../lib/i18n';
import { errorMessage } from '../../lib/http';

type Cell = 'none' | 'allow' | 'deny';
type Matrix = Record<string, Record<Action, Cell>>;
const NEXT: Record<Cell, Cell> = { none: 'allow', allow: 'deny', deny: 'none' };
const BASE_RESOURCES = ['record:*', 'metadata', 'workflow', 'identity', 'audit', 'file'];
const RES_LABEL: Record<string, string> = {
  'record:*': 'Hồ sơ (mọi loại)', metadata: 'Biểu mẫu', workflow: 'Quy trình', identity: 'Thành viên & vai trò', audit: 'Nhật ký kiểm toán', file: 'Tệp đính kèm',
};

function toMatrix(role: RoleView, resources: string[]): Matrix {
  const m: Matrix = {};
  for (const r of resources) m[r] = Object.fromEntries(ACTIONS.map((a) => [a, 'none'])) as Record<Action, Cell>;
  for (const g of role.permissions) {
    m[g.resource] ??= Object.fromEntries(ACTIONS.map((a) => [a, 'none'])) as Record<Action, Cell>;
    g.allow.forEach((a) => { m[g.resource][a] = 'allow'; });
    g.deny.forEach((a) => { m[g.resource][a] = 'deny'; });
  }
  return m;
}
function toGrants(m: Matrix): GrantInput[] {
  return Object.entries(m).map(([resource, cells]) => ({
    resource,
    allow: ACTIONS.filter((a) => cells[a] === 'allow'),
    deny: ACTIONS.filter((a) => cells[a] === 'deny'),
  })).filter((g) => g.allow.length || g.deny.length);
}

export default function Roles() {
  const roles = useRoles();
  const forms = useForms();
  const defs = useDefinitions();
  const { can } = usePermissions(['identity']);
  const canManage = can('identity', 'MANAGE');
  const [selected, setSelected] = useState<string>();
  const role = roles.data?.find((r) => r.code === selected) ?? roles.data?.[0];
  const resources = useMemo(() => {
    const extra = [...(forms.data?.map((f) => `record:${f.code}`) ?? []), ...(defs.data?.map((d) => `wf:${d.code}`) ?? [])];
    const fromRole = role?.permissions.map((p) => p.resource) ?? [];
    return [...new Set([...BASE_RESOURCES, ...extra, ...fromRole])];
  }, [forms.data, defs.data, role]);
  const [matrix, setMatrix] = useState<Matrix>({});
  const [saved, setSaved] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [nr, setNr] = useState({ code: '', name: '', description: '' });

  useEffect(() => { if (role) { setMatrix(toMatrix(role, resources)); setSaved(false); } }, [role, resources]);

  const savePerm = useIdentityMutation((g: GrantInput[]) => identityApi.setPermissions(role!.code, g));
  const create = useIdentityMutation(identityApi.createRole);
  const remove = useIdentityMutation(identityApi.deleteRole);
  const dirty = role ? JSON.stringify(toGrants(matrix)) !== JSON.stringify(toGrants(toMatrix(role, resources))) : false;

  const toggle = (r: string, a: Action) => {
    if (!canManage) return;
    setSaved(false);
    setMatrix((m) => ({ ...m, [r]: { ...m[r], [a]: NEXT[m[r][a]] } }));
  };

  return (
    <>
      <PageHeader title="Vai trò & Phân quyền" subtitle="Bấm vào ô để đổi: trống, cho phép, cấm. Cấm luôn thắng cho phép khi người dùng có nhiều vai trò."
        actions={canManage ? <Button onClick={() => setCreateOpen(true)}><Icon name="add" className="text-[18px] mr-1.5" />Vai trò mới</Button> : undefined} />
      <div className="grid grid-cols-1 xl:grid-cols-[260px_1fr] gap-6 items-start">
        <Panel title="Vai trò" padded={false}>
          {roles.isPending ? <div className="p-4"><ListSkeleton rows={4} withAvatar={false} /></div>
            : roles.error ? <div className="p-4"><ErrorState message={errorMessage(roles.error)} /></div>
            : roles.data.length === 0 ? <EmptyState title="Chưa có vai trò" />
            : (
              <ul className="p-2 space-y-0.5">
                {roles.data.map((r) => (
                  <li key={r.code}>
                    <button type="button" onClick={() => setSelected(r.code)} data-testid={`role-${r.code}`}
                      className={`w-full text-left rounded-lg px-3 py-2.5 ${r.code === role?.code ? 'bg-secondary-fixed/50' : 'hover:bg-surface-container-low'}`}>
                      <span className="flex items-center justify-between text-sm font-medium">{r.name}
                        {r.system ? <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-container text-outline">Hệ thống</span> : null}</span>
                      <span className="text-xs text-outline">{r.code}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
        </Panel>
        <Panel title={role ? `Ma trận quyền: ${role.name}` : 'Ma trận quyền'} padded={false}
          actions={role && canManage ? (
            <div className="flex gap-2">
              {!role.system ? <Button size="sm" variant="ghost" onClick={() => remove.mutate(role.code, { onSuccess: () => setSelected(undefined) })}>Xoá vai trò</Button> : null}
              <Button size="sm" disabled={!dirty || savePerm.isPending} onClick={() => savePerm.mutate(toGrants(matrix), { onSuccess: () => setSaved(true) })}>Lưu phân quyền</Button>
            </div>
          ) : undefined}>
          {roles.isPending || !role ? <TableSkeleton rows={6} cols={8} /> : (
            <>
              {saved ? <div className="px-5 pt-4"><Notice tone="success">Đã lưu. Quyền có hiệu lực ngay ở lần kiểm tra kế tiếp.</Notice></div> : null}
              {savePerm.error || remove.error ? <div className="px-5 pt-4"><ErrorState message={errorMessage(savePerm.error ?? remove.error)} /></div> : null}
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="perm-matrix">
                  <thead className="bg-surface-container-low text-xs text-on-surface-variant">
                    <tr><th className="px-5 h-10 text-left font-medium sticky left-0 bg-surface-container-low">Tài nguyên</th>
                      {ACTIONS.map((a) => <th key={a} className="px-1 font-medium min-w-[64px]">{label(t.action, a)}</th>)}</tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/20">
                    {resources.map((r) => (
                      <tr key={r} className="h-11">
                        <td className="px-5 sticky left-0 bg-surface-container-lowest">
                          <span className="font-medium">{RES_LABEL[r] ?? (r.startsWith('record:') ? `Hồ sơ ${r.slice(7)}` : r.startsWith('wf:') ? `Quy trình ${r.slice(3)} (xử lý việc)` : r)}</span>
                          <span className="block text-[11px] text-outline font-mono">{r}</span>
                        </td>
                        {ACTIONS.map((a) => {
                          const c = matrix[r]?.[a] ?? 'none';
                          return (
                            <td key={a} className="text-center">
                              <button type="button" disabled={!canManage} onClick={() => toggle(r, a)}
                                aria-label={`${r} ${label(t.action, a)}: ${c === 'allow' ? 'cho phép' : c === 'deny' ? 'cấm' : 'trống'}`}
                                className={`w-8 h-8 rounded-md inline-flex items-center justify-center transition-colors ${
                                  c === 'allow' ? 'bg-[#ECFDF5] text-[#047857]' : c === 'deny' ? 'bg-[#FEF2F2] text-[#B91C1C]' : 'bg-surface-container-low text-outline-variant hover:text-outline'}`}>
                                <Icon name={c === 'allow' ? 'check' : c === 'deny' ? 'block' : 'remove'} className="text-[18px]" />
                              </button>
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </Panel>
      </div>
      <Modal open={createOpen} title="Vai trò mới" onClose={() => setCreateOpen(false)}
        footer={<><Button variant="ghost" onClick={() => setCreateOpen(false)}>Huỷ</Button>
          <Button disabled={!/^[A-Z][A-Z0-9_]{1,63}$/.test(nr.code) || !nr.name || create.isPending}
            onClick={() => create.mutate({ code: nr.code, name: nr.name, description: nr.description || undefined },
              { onSuccess: (r) => { setCreateOpen(false); setSelected(r.code); setNr({ code: '', name: '', description: '' }); } })}>Tạo</Button></>}>
        <div className="space-y-4">
          <Field label="Mã" required htmlFor="rc" hint="Chữ in hoa, ví dụ ACCOUNTANT"><Input id="rc" value={nr.code} onChange={(e) => setNr({ ...nr, code: e.target.value.toUpperCase() })} /></Field>
          <Field label="Tên" required htmlFor="rn"><Input id="rn" value={nr.name} onChange={(e) => setNr({ ...nr, name: e.target.value })} /></Field>
          <Field label="Mô tả" htmlFor="rd"><Textarea id="rd" value={nr.description} onChange={(e) => setNr({ ...nr, description: e.target.value })} /></Field>
          {create.error ? <ErrorState message={errorMessage(create.error)} /> : null}
        </div>
      </Modal>
    </>
  );
}
