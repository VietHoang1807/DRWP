import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { metadataApi, useFormMutation, useForms } from '../../api/metadata';
import { usePermissions } from '../../api/identity';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Modal, PageHeader, StatusBadge, Textarea } from '../../components/ui/Primitives';
import { CardGridSkeleton } from '../../components/ui/Skeleton';
import { ApiError, errorMessage } from '../../lib/http';

const CODE = /^[A-Z][A-Z0-9_]{1,63}$/;

export default function FormList() {
  const forms = useForms();
  const { can } = usePermissions(['metadata']);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [v, setV] = useState({ code: '', name: '', description: '' });
  const create = useFormMutation(metadataApi.create);

  const submit = () => create.mutate({
    code: v.code, name: v.name, description: v.description || undefined,
    schema: { fields: [{ key: 'title', label: 'Tiêu đề', type: 'TEXT', required: true }] },
  }, { onSuccess: () => navigate(`/app/forms/${v.code}`) });

  return (
    <>
      <PageHeader title="Biểu mẫu" subtitle="Định nghĩa trường dữ liệu cho từng loại hồ sơ. Phiên bản đã phát hành không thể sửa, chỉ tạo phiên bản mới."
        actions={can('metadata', 'CONFIGURE') ? <Button onClick={() => setOpen(true)}><Icon name="add" className="text-[18px] mr-1.5" />Biểu mẫu mới</Button> : undefined} />
      {forms.isPending ? <CardGridSkeleton /> : forms.error ? <ErrorState message={errorMessage(forms.error)} /> : forms.data.length === 0 ? (
        <EmptyState icon="view_quilt" title="Chưa có biểu mẫu" description="Tạo biểu mẫu đầu tiên rồi phát hành để người dùng lập hồ sơ." />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {forms.data.map((f) => (
            <Link key={f.code} to={`/app/forms/${f.code}`} className="h-[168px] rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-5 hover:border-secondary transition-colors flex flex-col">
              <span className="flex items-start justify-between gap-2">
                <span className="font-semibold">{f.name}</span>
                <StatusBadge status={f.status} map={{ ACTIVE: 'Đang dùng', ARCHIVED: 'Lưu trữ' }} />
              </span>
              <span className="text-xs text-outline mt-1">{f.code}</span>
              <span className="text-sm text-on-surface-variant mt-2 line-clamp-2 flex-1">{f.description ?? ''}</span>
              <span className="flex gap-2 text-xs">
                {f.latestPublishedVersion ? <span className="px-2 py-0.5 rounded-full bg-[#ECFDF5] text-[#065F46]">Phát hành v{f.latestPublishedVersion}</span> : null}
                {f.draftVersion ? <span className="px-2 py-0.5 rounded-full bg-surface-container">Nháp v{f.draftVersion}</span> : null}
              </span>
            </Link>
          ))}
        </div>
      )}
      <Modal open={open} title="Biểu mẫu mới" onClose={() => setOpen(false)}
        footer={<><Button variant="ghost" onClick={() => setOpen(false)}>Huỷ</Button>
          <Button disabled={!CODE.test(v.code) || !v.name || create.isPending} onClick={submit}>Tạo và thiết kế</Button></>}>
        <div className="space-y-4">
          <Field label="Mã biểu mẫu" required hint="Chữ in hoa, số, gạch dưới. Ví dụ: LEAVE_REQUEST" htmlFor="fc"
            error={v.code && !CODE.test(v.code) ? 'Mã không hợp lệ' : undefined}>
            <Input id="fc" value={v.code} onChange={(e) => setV({ ...v, code: e.target.value.toUpperCase() })} />
          </Field>
          <Field label="Tên" required htmlFor="fn"><Input id="fn" value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} /></Field>
          <Field label="Mô tả" htmlFor="fd"><Textarea id="fd" value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} /></Field>
          {create.error ? <ErrorState message={create.error instanceof ApiError && create.error.status === 409 ? 'Mã biểu mẫu đã tồn tại.' : errorMessage(create.error)} /> : null}
        </div>
      </Modal>
    </>
  );
}
