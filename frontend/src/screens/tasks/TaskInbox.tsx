import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useInbox, useTask, useTaskMutation, workflowApi } from '../../api/workflow';
import { usePublishedForm } from '../../api/metadata';
import { useRecordFiles } from '../../api/records';
import { filesApi } from '../../api/misc';
import type { TaskDetail, TransitionOption } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, Icon, Modal, PageHeader, Pager, Panel, StatusBadge, Textarea } from '../../components/ui/Primitives';
import { DetailSkeleton, ListSkeleton } from '../../components/ui/Skeleton';
import { displayValue } from '../../components/forms/DynamicForm';
import { WorkflowTimeline } from '../../components/WorkflowTimeline';
import { fmt } from '../../lib/format';
import { t } from '../../lib/i18n';
import { errorMessage } from '../../lib/http';

/** Mã hồ sơ backend sinh theo mẫu <FORM>-<năm>-<số> */
const formOf = (recordCode: string) => recordCode.split('-').slice(0, -2).join('-');

const tone: Record<string, 'primary' | 'secondary' | 'destructive'> = { APPROVE: 'primary', REJECT: 'destructive' };

function Detail({ detail }: { detail: TaskDetail }) {
  const task = detail.task;
  const form = usePublishedForm(formOf(task.recordCode));
  const files = useRecordFiles(task.recordId);
  const [pending, setPending] = useState<TransitionOption | null>(null);
  const [comment, setComment] = useState('');
  const run = useTaskMutation((v: { code: string; comment?: string }) =>
    workflowApi.transition(task.id, v.code, v.comment, detail.instanceVersion), () => { setPending(null); setComment(''); });

  const fields = form.data?.schema.fields ?? [];
  const keys = Object.keys(detail.recordData);
  const act = (o: TransitionOption) => setPending(o); // luôn xác nhận; ý kiến bắt buộc nếu transition yêu cầu

  return (
    <div className="space-y-5">
      <Panel>
        <div className="flex flex-wrap items-start gap-3">
          <div className="flex-1 min-w-0">
            <p className="text-xs text-on-surface-variant">{task.definitionCode} · {task.stateCode}</p>
            <h2 className="text-lg font-semibold mt-0.5">{task.name}</h2>
            <p className="text-sm text-on-surface-variant mt-1">
              Hồ sơ <Link className="text-secondary font-medium" to={`/app/records/${task.recordId}`}>{task.recordCode}</Link> · giao {fmt.relative(task.createdAt)}
              {task.dueAt ? <> · <span className="text-[#92400E]">hạn {fmt.dateTime(task.dueAt)}</span></> : null}
            </p>
          </div>
          <StatusBadge status={task.status} map={t.taskStatus} />
        </div>
        {detail.assignedToMe && task.status === 'OPEN' ? (
          <div className="flex flex-wrap gap-2 mt-5 pt-4 border-t border-outline-variant/30" aria-label="Xử lý">
            {detail.transitions.map((o) => (
              <Button key={o.code} variant={tone[o.permission] ?? 'secondary'} disabled={!o.permitted || run.isPending}
                title={o.permitted ? undefined : `Cần quyền ${o.permission}`} onClick={() => act(o)} data-testid={`transition-${o.code}`}>
                {o.label}
              </Button>
            ))}
            {detail.transitions.length === 0 ? <p className="text-sm text-on-surface-variant">Không có thao tác hợp lệ với dữ liệu hiện tại.</p> : null}
          </div>
        ) : null}
        {run.error && !pending ? <div className="mt-3"><ErrorState message={errorMessage(run.error)} /></div> : null}
      </Panel>

      <Panel title="Nội dung hồ sơ">
        {form.isPending ? <ListSkeleton rows={3} withAvatar={false} /> : (
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {(fields.length ? fields.map((f) => f.key) : keys).filter((k) => k in detail.recordData).map((k) => {
              const f = fields.find((x) => x.key === k);
              return (
                <div key={k}>
                  <dt className="text-xs text-on-surface-variant">{f?.label ?? k}</dt>
                  <dd className="text-sm mt-0.5">{f ? displayValue(f, detail.recordData[k], files.data) : String(detail.recordData[k])}</dd>
                </div>
              );
            })}
          </dl>
        )}
        {files.data?.length ? (
          <ul className="mt-5 pt-4 border-t border-outline-variant/30 flex flex-wrap gap-2">
            {files.data.map((f) => (
              <li key={f.id}>
                <button type="button" onClick={() => void filesApi.download(f)} className="inline-flex items-center gap-1.5 h-8 px-3 rounded-full border border-outline-variant text-sm hover:border-secondary">
                  <Icon name="attach_file" className="text-[16px]" />{f.name}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </Panel>

      <Panel title="Lịch sử xử lý"><WorkflowTimeline history={detail.history} /></Panel>

      <Modal open={!!pending} title={pending ? `${pending.label}: ${task.recordCode}` : ''} onClose={() => setPending(null)}
        footer={<>
          <Button variant="ghost" onClick={() => setPending(null)}>Đóng</Button>
          <Button variant={pending ? tone[pending.permission] ?? 'primary' : 'primary'} data-testid="confirm-transition"
            disabled={run.isPending || (pending?.commentRequired && !comment.trim())}
            onClick={() => pending && run.mutate({ code: pending.code, comment: comment.trim() || undefined })}>
            {run.isPending ? 'Đang xử lý…' : 'Xác nhận'}
          </Button>
        </>}>
        <label htmlFor="comment" className="block text-[13px] font-medium mb-1.5">
          Ý kiến {pending?.commentRequired ? <span className="text-error">*</span> : '(không bắt buộc)'}
        </label>
        <Textarea id="comment" value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Ghi chú cho người nộp và lịch sử xử lý" />
        {run.error ? <div className="mt-3"><ErrorState message={errorMessage(run.error)} /></div> : null}
      </Modal>
    </div>
  );
}

export default function TaskInbox() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const inbox = useInbox(page, 20);
  const selected = params.get('task') ?? inbox.data?.items[0]?.id;
  const detail = useTask(selected);
  const items = useMemo(() => inbox.data?.items ?? [], [inbox.data]);

  return (
    <>
      <PageHeader title="Hộp việc & Phê duyệt" subtitle="Các bước quy trình đang giao cho bạn hoặc vai trò của bạn." />
      <div className="grid grid-cols-1 xl:grid-cols-[380px_1fr] gap-6 items-start">
        <Panel padded={false}>
          {inbox.isPending ? <ListSkeleton rows={6} /> : inbox.error ? <div className="p-4"><ErrorState message={errorMessage(inbox.error)} /></div>
            : items.length === 0 ? <EmptyState icon="done_all" title="Bạn đã xử lý hết việc" description="Việc mới sẽ xuất hiện khi có hồ sơ được nộp." />
            : (
              <ul className="divide-y divide-outline-variant/20" aria-label="Danh sách việc">
                {items.map((x) => (
                  <li key={x.id}>
                    <button type="button" onClick={() => setParams({ task: x.id, ...(page ? { page: String(page) } : {}) })}
                      aria-current={x.id === selected}
                      className={`w-full text-left px-4 py-3 flex gap-3 ${x.id === selected ? 'bg-secondary-fixed/40' : 'hover:bg-surface-container-low'}`}>
                      <span className="w-9 h-9 shrink-0 rounded-full bg-surface-container text-secondary flex items-center justify-center"><Icon name="assignment" className="text-[18px]" /></span>
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-medium truncate">{x.recordCode}</span>
                        <span className="block text-xs text-on-surface-variant truncate">{x.name} · {x.assigneeType === 'ROLE' ? x.assigneeValue : 'giao riêng'}</span>
                      </span>
                      <span className="text-[11px] text-outline whitespace-nowrap">{fmt.relative(x.createdAt)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          {inbox.data && inbox.data.total > 20 ? <Pager page={page} size={20} total={inbox.data.total} onPage={(p) => setParams({ page: String(p) })} /> : null}
        </Panel>
        <div>
          {!selected ? null : detail.isPending ? <DetailSkeleton /> : detail.error ? <ErrorState message={errorMessage(detail.error)} />
            : <Detail key={detail.data.task.id} detail={detail.data} />}
        </div>
      </div>
    </>
  );
}
