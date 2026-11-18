import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useForms, usePublishedForm } from '../../api/metadata';
import { recordsApi, useRecordMutation } from '../../api/records';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, Icon, PageHeader, Panel } from '../../components/ui/Primitives';
import { CardGridSkeleton, FormSkeleton } from '../../components/ui/Skeleton';
import { DynamicForm, violationsToErrors } from '../../components/forms/DynamicForm';
import { ApiError, errorMessage } from '../../lib/http';

export default function RecordCreate() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const formCode = params.get('form') ?? undefined;
  const forms = useForms();
  const published = usePublishedForm(formCode);
  const [data, setData] = useState<Record<string, unknown>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const create = useRecordMutation(recordsApi.create, (r) => navigate(`/app/records/${r.id}`, { replace: true }));

  const save = (submit: boolean) => {
    setErrors({});
    setFormError(null);
    create.mutate({ formCode: formCode!, data, submit }, {
      onError: (e) => {
        if (e instanceof ApiError && e.violations.length) setErrors(violationsToErrors(e.violations));
        setFormError(errorMessage(e));
      },
    });
  };

  if (!formCode) {
    const available = forms.data?.filter((f) => f.status === 'ACTIVE' && f.latestPublishedVersion) ?? [];
    return (
      <>
        <PageHeader title="Lập hồ sơ mới" subtitle="Chọn loại hồ sơ. Hồ sơ sẽ dùng phiên bản biểu mẫu đang phát hành." />
        {forms.isPending ? <CardGridSkeleton count={3} /> : forms.error ? <ErrorState message={errorMessage(forms.error)} />
          : available.length === 0 ? <Panel><EmptyState icon="view_quilt" title="Chưa có biểu mẫu nào được phát hành" description="Quản trị đơn vị cần tạo và phát hành biểu mẫu trước." /></Panel>
          : (
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              {available.map((f) => (
                <button key={f.code} type="button" onClick={() => setParams({ form: f.code })} data-testid={`pick-form-${f.code}`}
                  className="text-left h-[168px] rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-5 hover:border-secondary hover:shadow-sm transition">
                  <span className="w-10 h-10 rounded-lg bg-secondary-fixed/60 text-secondary flex items-center justify-center"><Icon name="description" /></span>
                  <span className="block mt-4 font-semibold">{f.name}</span>
                  <span className="block mt-1 text-sm text-on-surface-variant line-clamp-2">{f.description ?? f.code}</span>
                </button>
              ))}
            </div>
          )}
      </>
    );
  }

  return (
    <>
      <PageHeader title={published.data ? `Lập hồ sơ: ${forms.data?.find((f) => f.code === formCode)?.name ?? formCode}` : 'Lập hồ sơ'}
        subtitle={published.data ? `Biểu mẫu ${formCode} · phiên bản ${published.data.versionNo}` : undefined}
        actions={<Link to="/app/records/new" className="text-sm text-secondary">Đổi loại hồ sơ</Link>} />
      <Panel>
        {published.isPending ? <FormSkeleton fields={6} /> : published.error ? <ErrorState message={errorMessage(published.error)} /> : (
          <>
            <DynamicForm schema={published.data.schema} value={data} onChange={setData} errors={errors} />
            {formError ? <div className="mt-5"><ErrorState message={formError} /></div> : null}
            <div className="flex justify-end gap-2 mt-6 pt-5 border-t border-outline-variant/30">
              <Button variant="secondary" disabled={create.isPending} onClick={() => save(false)}>Lưu nháp</Button>
              <Button disabled={create.isPending} onClick={() => save(true)} data-testid="submit-record">
                <Icon name="send" className="text-[18px] mr-1.5" />{create.isPending ? 'Đang gửi…' : 'Nộp hồ sơ'}
              </Button>
            </div>
          </>
        )}
      </Panel>
    </>
  );
}
