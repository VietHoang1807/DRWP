import { useState } from 'react';
import { identityApi, useIdentityMutation, useMembers, usePermissions, useRoles } from '../../api/identity';
import type { MemberView } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Modal, PageHeader, Panel } from '../../components/ui/Primitives';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { errorMessage } from '../../lib/http';

function RoleChecks({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const roles = useRoles();
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium mb-1">Vai trò</legend>
      {roles.data?.map((r) => (
        <label key={r.code} className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={value.includes(r.code)}
            onChange={(e) => onChange(e.target.checked ? [...value, r.code] : value.filter((x) => x !== r.code))} />
          {r.name} <span className="text-xs text-outline">{r.code}</span>
        </label>
      ))}
    </fieldset>
  );
}

export default function Members() {
  const members = useMembers();
  const { can } = usePermissions(['identity']);
  const canManage = can('identity', 'MANAGE');
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ subject: '', displayName: '', roles: [] as string[] });
  const [editing, setEditing] = useState<MemberView | null>(null);
  const [editRoles, setEditRoles] = useState<string[]>([]);
  const [removing, setRemoving] = useState<MemberView | null>(null);
  const add = useIdentityMutation(identityApi.addMember);
  const setRoles = useIdentityMutation((v: { subject: string; roles: string[] }) => identityApi.setMemberRoles(v.subject, v.roles));
  const remove = useIdentityMutation(identityApi.removeMember);

  return (
    <>
      <PageHeader title="Thành viên" subtitle="Người dùng (theo mã định danh từ hệ thống đăng nhập) được phép làm việc trong đơn vị này."
        actions={canManage ? <Button onClick={() => setAdding(true)}><Icon name="person_add" className="text-[18px] mr-1.5" />Thêm thành viên</Button> : undefined} />
      <Panel padded={false}>
        {members.isPending ? <TableSkeleton rows={5} cols={4} />
          : members.error ? <div className="p-5"><ErrorState message={errorMessage(members.error)} onRetry={() => members.refetch()} /></div>
          : members.data.length === 0 ? <EmptyState icon="group" title="Chưa có thành viên" />
          : (
            <table className="w-full text-sm">
              <thead className="bg-surface-container-low text-xs text-left text-on-surface-variant">
                <tr><th className="px-5 h-10 font-medium">Thành viên</th><th className="px-4 font-medium">Mã định danh</th><th className="px-4 font-medium">Vai trò</th><th className="px-4 font-medium">Trạng thái</th><th /></tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {members.data.map((m) => (
                  <tr key={m.subject} className="h-14">
                    <td className="px-5">
                      <span className="flex items-center gap-3">
                        <span className="w-8 h-8 rounded-full bg-secondary-fixed text-on-secondary-fixed text-xs font-bold flex items-center justify-center uppercase">{(m.displayName ?? m.subject).slice(0, 2)}</span>
                        <span className="font-medium">{m.displayName ?? m.subject}</span>
                      </span>
                    </td>
                    <td className="px-4 font-mono text-xs text-on-surface-variant">{m.subject}</td>
                    <td className="px-4"><span className="flex flex-wrap gap-1">{m.roles.map((r) => <span key={r} className="px-2 py-0.5 rounded-full bg-surface-container text-xs">{r}</span>)}</span></td>
                    <td className="px-4 text-xs">{m.status === 'ACTIVE' ? 'Hoạt động' : m.status}</td>
                    <td className="px-4 text-right whitespace-nowrap">
                      {canManage ? (
                        <>
                          <Button size="sm" variant="ghost" onClick={() => { setEditing(m); setEditRoles(m.roles); }}>Sửa vai trò</Button>
                          <Button size="sm" variant="ghost" onClick={() => setRemoving(m)}>Gỡ</Button>
                        </>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Panel>

      <Modal open={adding} title="Thêm thành viên" onClose={() => setAdding(false)}
        footer={<><Button variant="ghost" onClick={() => setAdding(false)}>Huỷ</Button>
          <Button disabled={!form.subject || add.isPending}
            onClick={() => add.mutate({ subject: form.subject.trim(), displayName: form.displayName || undefined, roles: form.roles },
              { onSuccess: () => { setAdding(false); setForm({ subject: '', displayName: '', roles: [] }); } })}>Thêm</Button></>}>
        <div className="space-y-4">
          <Field label="Mã định danh (sub)" required htmlFor="ms" hint="Giá trị claim sub trong token, ví dụ alice"><Input id="ms" value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} /></Field>
          <Field label="Tên hiển thị" htmlFor="md"><Input id="md" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></Field>
          <RoleChecks value={form.roles} onChange={(roles) => setForm({ ...form, roles })} />
          {add.error ? <ErrorState message={errorMessage(add.error)} /> : null}
        </div>
      </Modal>

      <Modal open={!!editing} title={`Vai trò của ${editing?.displayName ?? editing?.subject ?? ''}`} onClose={() => setEditing(null)}
        footer={<><Button variant="ghost" onClick={() => setEditing(null)}>Huỷ</Button>
          <Button disabled={setRoles.isPending} onClick={() => setRoles.mutate({ subject: editing!.subject, roles: editRoles }, { onSuccess: () => setEditing(null) })}>Lưu</Button></>}>
        <RoleChecks value={editRoles} onChange={setEditRoles} />
        {setRoles.error ? <div className="mt-3"><ErrorState message={errorMessage(setRoles.error)} /></div> : null}
      </Modal>

      <Modal open={!!removing} title="Gỡ thành viên" onClose={() => setRemoving(null)}
        footer={<><Button variant="ghost" onClick={() => setRemoving(null)}>Huỷ</Button>
          <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(removing!.subject, { onSuccess: () => setRemoving(null) })}>Gỡ khỏi đơn vị</Button></>}>
        <p className="text-sm">Người dùng <b>{removing?.subject}</b> sẽ không truy cập được đơn vị này nữa. Hồ sơ và lịch sử của họ vẫn được giữ.</p>
        {remove.error ? <div className="mt-3"><ErrorState message={errorMessage(remove.error)} /></div> : null}
      </Modal>
    </>
  );
}
