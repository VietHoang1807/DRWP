import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useDefinitions, useWorkflowMutation, workflowApi } from '../../api/workflow';
import { useForms } from '../../api/metadata';
import { usePermissions } from '../../api/identity';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Modal, PageHeader, Select, StatusBadge } from '../../components/ui/Primitives';
import { CardGridSkeleton } from '../../components/ui/Skeleton';
import { errorMessage } from '../../lib/http';
import { twoLevelTemplate } from './templates';

const CODE = /^[A-Z][A-Z0-9_]{1,63}$/;

export default function WorkflowList() {
  const defs = useDefinitions();
  const forms = useForms();
  const { can } = usePermissions(['workflow']);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [v, setV] = useState({ code: '', name: '', formCode: '' });
  const create = useWorkflowMutation(workflowApi.create);
  const bound = new Set(defs.data?.filter((d) => d.status === 'ACTIVE').map((d) => d.formCode));

  return (
    <>
      <PageHeader title="Quy trình" subtitle="Mỗi loại hồ sơ gắn với một quy trình đang dùng. Phiên bản mới chỉ áp dụng cho lần nộp sau."
        actions={can('workflow', 'CONFIGURE') ? <Button onClick={() => setOpen(true)}><Icon name="add" className="text-[18px] mr-1.5" />Quy trình mới</Button> : undefined} />
      {defs.isPending ? <CardGridSkeleton /> : defs.error ? <ErrorState message={errorMessage(defs.error)} /> : defs.data.length === 0 ? (
        <EmptyState icon="account_tree" title="Chưa có quy trình" description="Hồ sơ nộp lên sẽ dừng ở trạng thái Đã nộp nếu loại hồ sơ chưa có quy trình." />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {defs.data.map((d) => (
            <Link key={d.code} to={`/app/workflows/${d.code}`} className="h-[168px] rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-5 hover:border-secondary flex flex-col">
              <span className="flex items-start justify-between gap-2">
                <span className="font-semibold">{d.name}</span>
                <StatusBadge status={d.status} map={{ ACTIVE: 'Đang dùng', ARCHIVED: 'Lưu trữ' }} />
              </span>
              <span className="text-xs text-outline mt-1">{d.code}</span>
              <span className="text-sm text-on-surface-variant mt-2 flex-1">Loại hồ sơ: <b className="text-on-surface">{d.formCode}</b></span>
              <span className="flex gap-2 text-xs">
                {d.latestPublishedVersion ? <span className="px-2 py-0.5 rounded-full bg-[#ECFDF5] text-[#065F46]">Phát hành v{d.latestPublishedVersion}</span> : null}
                {d.draftVersion ? <span className="px-2 py-0.5 rounded-full bg-surface-container">Nháp v{d.draftVersion}</span> : null}
              </span>
            </Link>
          ))}
        </div>
      )}
      <Modal open={open} title="Quy trình mới" onClose={() => setOpen(false)}
        footer={<><Button variant="ghost" onClick={() => setOpen(false)}>Huỷ</Button>
          <Button disabled={!CODE.test(v.code) || !v.name || !v.formCode || create.isPending}
            onClick={() => create.mutate({ ...v, spec: twoLevelTemplate }, { onSuccess: () => navigate(`/app/workflows/${v.code}`) })}>Tạo từ mẫu 2 cấp duyệt</Button></>}>
        <div className="space-y-4">
          <Field label="Mã quy trình" required htmlFor="wc" hint="Ví dụ: LEAVE_APPROVAL">
            <Input id="wc" value={v.code} onChange={(e) => setV({ ...v, code: e.target.value.toUpperCase() })} />
          </Field>
          <Field label="Tên" required htmlFor="wn"><Input id="wn" value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} /></Field>
          <Field label="Áp dụng cho loại hồ sơ" required htmlFor="wf">
            <Select id="wf" value={v.formCode} onChange={(e) => setV({ ...v, formCode: e.target.value })}>
              <option value="">— Chọn biểu mẫu —</option>
              {forms.data?.map((f) => <option key={f.code} value={f.code} disabled={bound.has(f.code)}>{f.name}{bound.has(f.code) ? ' (đã có quy trình)' : ''}</option>)}
            </Select>
          </Field>
          {create.error ? <ErrorState message={errorMessage(create.error)} /> : null}
        </div>
      </Modal>
    </>
  );
}
