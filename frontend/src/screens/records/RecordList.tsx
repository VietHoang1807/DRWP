import { useMemo, useState, useTransition } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useSession } from '../../auth/session';
import { useForms, usePublishedForm } from '../../api/metadata';
import { useRecords } from '../../api/records';
import { RECORD_STATUSES, type RecordStatus } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Icon, PageHeader, Pager, Panel, Select, StatusBadge } from '../../components/ui/Primitives';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { t, label } from '../../lib/i18n';
import { fmt } from '../../lib/format';
import { errorMessage } from '../../lib/http';

export default function RecordList() {
  const session = useSession()!;
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const [, startTransition] = useTransition();
  const formCode = params.get('form') ?? '';
  const status = (params.get('status') ?? '') as RecordStatus | '';
  const mine = params.get('mine') === '1';
  const page = Number(params.get('page') ?? 0);
  const [code, setCode] = useState(params.get('code') ?? '');

  const forms = useForms();
  const published = usePublishedForm(formCode || undefined);
  const filterable = useMemo(() => published.data?.schema.fields.filter((f) => f.filterable) ?? [], [published.data]);
  const dataFilters = useMemo(() => {
    const out: Record<string, string> = {};
    for (const f of filterable) {
      const v = params.get(`data.${f.key}`);
      if (v) out[f.key] = v;
    }
    return out;
  }, [filterable, params]);

  const records = useRecords({
    formCode: formCode || undefined, status: status || undefined, createdBy: mine ? session.subject : undefined,
    recordCode: params.get('code') || undefined, data: dataFilters, page, size: 20, sort: 'UPDATED_AT', desc: true,
  });

  const update = (patch: Record<string, string | null>) => startTransition(() => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!('page' in patch)) next.delete('page');
    setParams(next, { replace: true });
  });

  return (
    <>
      <PageHeader title="Hồ sơ" subtitle="Tra cứu hồ sơ bạn được phép xem. Không có quyền xem loại hồ sơ thì chỉ thấy hồ sơ của mình."
        actions={<Link to="/app/records/new"><Button><Icon name="add" className="text-[18px] mr-1.5" />Lập hồ sơ</Button></Link>} />

      <Panel padded={false}>
        <div className="flex flex-wrap items-end gap-3 p-4 border-b border-outline-variant/30">
          <label className="w-56 text-xs text-on-surface-variant space-y-1">Loại hồ sơ
            <Select value={formCode} onChange={(e) => update({ form: e.target.value, ...Object.fromEntries(filterable.map((f) => [`data.${f.key}`, null])) })}>
              <option value="">Tất cả</option>
              {forms.data?.map((f) => <option key={f.code} value={f.code}>{f.name}</option>)}
            </Select>
          </label>
          <label className="w-44 text-xs text-on-surface-variant space-y-1">Trạng thái
            <Select value={status} onChange={(e) => update({ status: e.target.value })}>
              <option value="">Tất cả</option>
              {RECORD_STATUSES.map((s) => <option key={s} value={s}>{label(t.recordStatus, s)}</option>)}
            </Select>
          </label>
          {filterable.map((f) => (
            <label key={f.key} className="w-44 text-xs text-on-surface-variant space-y-1">{f.label ?? f.key}
              {f.options?.length ? (
                <Select value={params.get(`data.${f.key}`) ?? ''} onChange={(e) => update({ [`data.${f.key}`]: e.target.value })}>
                  <option value="">Tất cả</option>
                  {f.options.map((o) => <option key={o} value={o}>{o}</option>)}
                </Select>
              ) : (
                <Input defaultValue={params.get(`data.${f.key}`) ?? ''} onBlur={(e) => update({ [`data.${f.key}`]: e.target.value })} />
              )}
            </label>
          ))}
          <form className="w-52 text-xs text-on-surface-variant space-y-1" onSubmit={(e) => { e.preventDefault(); update({ code: code.trim() }); }}>
            <label htmlFor="rec-code">Mã hồ sơ</label>
            <Input id="rec-code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="LEAVE-2026-000001" />
          </form>
          <label className="flex items-center gap-2 text-sm h-[38px] ml-auto">
            <input type="checkbox" checked={mine} onChange={(e) => update({ mine: e.target.checked ? '1' : null })} />Chỉ hồ sơ của tôi
          </label>
        </div>

        {records.isPending ? <TableSkeleton rows={8} cols={6} /> : records.error ? (
          <div className="p-4"><ErrorState message={errorMessage(records.error)} onRetry={() => void records.refetch()} /></div>
        ) : records.data.items.length === 0 ? (
          <EmptyState icon="search_off" title="Không có hồ sơ phù hợp" description="Thử bỏ bớt bộ lọc hoặc lập hồ sơ mới." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-surface-container-low text-left text-xs text-on-surface-variant">
                <tr>
                  <th className="px-4 h-10 font-medium">Mã hồ sơ</th>
                  <th className="px-4 font-medium">Loại</th>
                  <th className="px-4 font-medium">Người lập</th>
                  <th className="px-4 font-medium">Trạng thái</th>
                  <th className="px-4 font-medium">Ngày nộp</th>
                  <th className="px-4 font-medium">Tạo lúc</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20">
                {records.data.items.map((r) => (
                  <tr key={r.id} onClick={() => navigate(`/app/records/${r.id}`)} className="h-14 cursor-pointer hover:bg-surface-container-low">
                    <td className="px-4 font-medium text-primary"><Link to={`/app/records/${r.id}`} onClick={(e) => e.stopPropagation()}>{r.recordCode}</Link></td>
                    <td className="px-4">{r.formCode} <span className="text-xs text-outline">v{r.formVersionNo}</span></td>
                    <td className="px-4">{r.owner === session.subject ? 'Tôi' : r.owner}</td>
                    <td className="px-4"><StatusBadge status={r.status} /></td>
                    <td className="px-4 text-on-surface-variant">{fmt.dateTime(r.submittedAt)}</td>
                    <td className="px-4 text-on-surface-variant">{fmt.relative(r.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {records.data ? <Pager page={page} size={20} total={records.data.total} onPage={(p) => update({ page: String(p) })} /> : null}
      </Panel>
    </>
  );
}
