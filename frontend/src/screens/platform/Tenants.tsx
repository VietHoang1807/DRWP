import { useState } from 'react';
import { platformApi, usePlatformMutation, usePlatformTenants, type CreateTenantInput } from '../../api/platform';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Modal, PageHeader, Panel, StatusBadge } from '../../components/ui/Primitives';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { t } from '../../lib/i18n';
import { fmt } from '../../lib/format';
import { errorMessage } from '../../lib/http';

const ID = /^[a-z][a-z0-9-]{1,39}$/;
const empty: CreateTenantInput = { tenantId: '', displayName: '', adminSubject: '', adminDisplayName: '', complianceProfiles: [] };

export default function Tenants() {
  const q = usePlatformTenants();
  const [open, setOpen] = useState(false);
  const [v, setV] = useState(empty);
  const [profiles, setProfiles] = useState('');
  const create = usePlatformMutation(platformApi.create);
  const act = usePlatformMutation((x: { id: string; a: 'suspend' | 'activate' | 'migrate' }) => platformApi.action(x.id, x.a));

  return (
    <>
      <PageHeader title="Đơn vị (tenant)" subtitle="Mỗi đơn vị có schema dữ liệu riêng. Tạo đơn vị sẽ chạy migration và gán quản trị viên đầu tiên."
        actions={<Button onClick={() => setOpen(true)}><Icon name="add_business" className="text-[18px] mr-1.5" />Tạo đơn vị</Button>} />
      <Panel padded={false}>
        {q.isPending ? <TableSkeleton rows={4} cols={6} />
          : q.error ? <div className="p-5"><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
          : q.data.length === 0 ? <EmptyState icon="domain" title="Chưa có đơn vị nào" />
          : (
            <table className="w-full text-sm">
              <thead className="bg-surface-container-low text-xs text-left text-on-surface-variant">
                <tr><th className="px-5 h-10 font-medium">Đơn vị</th><th className="px-4 font-medium">Trạng thái</th><th className="px-4 font-medium">Schema</th>
                  <th className="px-4 font-medium">Hồ sơ tuân thủ</th><th className="px-4 font-medium">Tạo lúc</th><th /></tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {q.data.map((x) => (
                  <tr key={x.tenantId} className="h-14 align-middle">
                    <td className="px-5"><span className="font-medium">{x.displayName}</span><span className="block text-xs text-outline font-mono">{x.tenantId}</span>
                      {x.lastError ? <span className="block text-xs text-error mt-0.5">{x.lastError}</span> : null}</td>
                    <td className="px-4"><StatusBadge status={x.status} map={t.tenantStatus} /></td>
                    <td className="px-4 font-mono text-xs">{x.schemaName ?? '—'}</td>
                    <td className="px-4 text-xs">{x.complianceProfiles.join(', ') || '—'}</td>
                    <td className="px-4 text-xs whitespace-nowrap">{fmt.dateTime(x.createdAt)}</td>
                    <td className="px-4 text-right whitespace-nowrap">
                      {x.status === 'ACTIVE' ? <Button size="sm" variant="ghost" onClick={() => act.mutate({ id: x.tenantId, a: 'suspend' })}>Tạm dừng</Button> : null}
                      {x.status === 'SUSPENDED' ? <Button size="sm" variant="ghost" onClick={() => act.mutate({ id: x.tenantId, a: 'activate' })}>Kích hoạt</Button> : null}
                      <Button size="sm" variant="ghost" onClick={() => act.mutate({ id: x.tenantId, a: 'migrate' })}>Chạy migration</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        {act.error ? <div className="p-4"><ErrorState message={errorMessage(act.error)} /></div> : null}
      </Panel>
      <Modal open={open} title="Tạo đơn vị" onClose={() => setOpen(false)}
        footer={<><Button variant="ghost" onClick={() => setOpen(false)}>Huỷ</Button>
          <Button disabled={!ID.test(v.tenantId) || !v.displayName || !v.adminSubject || create.isPending}
            onClick={() => create.mutate({ ...v, adminDisplayName: v.adminDisplayName || undefined,
              complianceProfiles: profiles.split(',').map((s) => s.trim()).filter(Boolean) },
            { onSuccess: () => { setOpen(false); setV(empty); setProfiles(''); } })}>{create.isPending ? 'Đang khởi tạo…' : 'Tạo'}</Button></>}>
        <div className="space-y-4">
          <Field label="Mã đơn vị" required htmlFor="ti" hint="Chữ thường, số, gạch ngang; ví dụ so-tai-chinh"><Input id="ti" value={v.tenantId} onChange={(e) => setV({ ...v, tenantId: e.target.value.toLowerCase() })} /></Field>
          <Field label="Tên hiển thị" required htmlFor="tn"><Input id="tn" value={v.displayName} onChange={(e) => setV({ ...v, displayName: e.target.value })} /></Field>
          <Field label="Quản trị viên đầu tiên (sub)" required htmlFor="ta"><Input id="ta" value={v.adminSubject} onChange={(e) => setV({ ...v, adminSubject: e.target.value })} /></Field>
          <Field label="Tên quản trị viên" htmlFor="tan"><Input id="tan" value={v.adminDisplayName} onChange={(e) => setV({ ...v, adminDisplayName: e.target.value })} /></Field>
          <Field label="Hồ sơ tuân thủ" htmlFor="tp" hint="Phân tách bằng dấu phẩy, ví dụ ND13"><Input id="tp" value={profiles} onChange={(e) => setProfiles(e.target.value)} /></Field>
          {create.error ? <ErrorState message={errorMessage(create.error)} /> : null}
        </div>
      </Modal>
    </>
  );
}
