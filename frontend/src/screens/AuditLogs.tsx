import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useAudit, type AuditFilter } from '../api/misc';
import type { AuditRow } from '../api/types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { EmptyState, ErrorState, Field, Modal, PageHeader, Pager, Panel, Select } from '../components/ui/Primitives';
import { TableSkeleton } from '../components/ui/Skeleton';
import { fmt } from '../lib/format';
import { errorMessage } from '../lib/http';

const SIZE = 25;
const OUTCOME: Record<string, string> = { SUCCESS: 'bg-[#ECFDF5] text-[#065F46]', DENIED: 'bg-[#FFFBEB] text-[#92400E]', FAILED: 'bg-[#FEF2F2] text-[#991B1B]' };
const OUTCOME_VI: Record<string, string> = { SUCCESS: 'Thành công', DENIED: 'Bị từ chối', FAILED: 'Lỗi' };
const KEYS = ['actor', 'action', 'resourceType', 'resourceId', 'outcome'] as const;

export default function AuditLogs() {
  const [params, setParams] = useSearchParams();
  const filter: AuditFilter = { page: Number(params.get('page') ?? 0), size: SIZE };
  for (const k of KEYS) { const v = params.get(k); if (v) filter[k] = v; }
  const q = useAudit(filter);
  const [draft, setDraft] = useState<Record<string, string>>(() => Object.fromEntries(KEYS.map((k) => [k, params.get(k) ?? ''])));
  const [detail, setDetail] = useState<AuditRow | null>(null);

  const apply = () => {
    const next = new URLSearchParams();
    for (const k of KEYS) if (draft[k]) next.set(k, draft[k]);
    setParams(next);
  };

  return (
    <>
      <PageHeader title="Nhật ký kiểm toán" subtitle="Sự kiện nghiệp vụ và truy cập bị từ chối trong đơn vị, chỉ đọc và không sửa được." />
      <Panel className="mb-6">
        <form className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3 items-end" onSubmit={(e) => { e.preventDefault(); apply(); }}>
          <Field label="Người thực hiện" htmlFor="a-actor"><Input id="a-actor" value={draft.actor} onChange={(e) => setDraft({ ...draft, actor: e.target.value })} /></Field>
          <Field label="Hành động" htmlFor="a-action"><Input id="a-action" placeholder="RECORD_SUBMITTED" value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })} /></Field>
          <Field label="Loại đối tượng" htmlFor="a-rt"><Input id="a-rt" placeholder="record" value={draft.resourceType} onChange={(e) => setDraft({ ...draft, resourceType: e.target.value })} /></Field>
          <Field label="Mã đối tượng" htmlFor="a-rid"><Input id="a-rid" value={draft.resourceId} onChange={(e) => setDraft({ ...draft, resourceId: e.target.value })} /></Field>
          <Field label="Kết quả" htmlFor="a-out">
            <Select id="a-out" value={draft.outcome} onChange={(e) => setDraft({ ...draft, outcome: e.target.value })}>
              <option value="">Tất cả</option>
              {Object.entries(OUTCOME_VI).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </Select>
          </Field>
          <div className="flex gap-2"><Button type="submit">Lọc</Button>
            <Button type="button" variant="ghost" onClick={() => { setDraft(Object.fromEntries(KEYS.map((k) => [k, '']))); setParams(new URLSearchParams()); }}>Xoá lọc</Button></div>
        </form>
      </Panel>
      <Panel padded={false}>
        {q.isPending ? <TableSkeleton rows={8} cols={6} />
          : q.error ? <div className="p-5"><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
          : q.data.items.length === 0 ? <EmptyState icon="policy" title="Không có sự kiện phù hợp" />
          : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-surface-container-low text-xs text-left text-on-surface-variant">
                  <tr><th className="px-5 h-10 font-medium">Thời điểm</th><th className="px-4 font-medium">Người thực hiện</th><th className="px-4 font-medium">Hành động</th>
                    <th className="px-4 font-medium">Đối tượng</th><th className="px-4 font-medium">Kết quả</th><th className="px-4 font-medium">Mã tương quan</th></tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {q.data.items.map((r) => (
                    <tr key={r.id} className="h-12 hover:bg-surface-container-low cursor-pointer" onClick={() => setDetail(r)}>
                      <td className="px-5 whitespace-nowrap tabular-nums">{fmt.dateTime(r.occurredAt)}</td>
                      <td className="px-4">{r.actor ?? 'hệ thống'}</td>
                      <td className="px-4 font-medium">{r.action}</td>
                      <td className="px-4 text-on-surface-variant">{r.resourceType ?? '—'}{r.resourceId ? <span className="text-xs text-outline"> · {r.resourceId.slice(0, 8)}</span> : null}</td>
                      <td className="px-4"><span className={`px-2 py-0.5 rounded-full text-xs font-medium ${OUTCOME[r.outcome]}`}>{OUTCOME_VI[r.outcome]}</span></td>
                      <td className="px-4 text-xs text-outline font-mono">{r.correlationId?.slice(0, 12) ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        {q.data ? <div className="p-4"><Pager page={filter.page!} size={SIZE} total={q.data.total}
          onPage={(p) => { const n = new URLSearchParams(params); n.set('page', String(p)); setParams(n); }} /></div> : null}
      </Panel>
      <Modal open={!!detail} title={detail ? `Sự kiện #${detail.id}` : ''} onClose={() => setDetail(null)} wide>
        <pre className="text-xs bg-surface-container-low rounded-lg p-4 overflow-auto max-h-[60vh]">{detail ? JSON.stringify(detail, null, 2) : ''}</pre>
      </Modal>
    </>
  );
}
