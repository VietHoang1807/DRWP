import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useSession, useTenantId } from '../../auth/session';
import { useFormVersion } from '../../api/metadata';
import { recordsApi, useRecord, useRecordFiles, useRecordInstances, useRecordMutation, useRevisions } from '../../api/records';
import { workflowApi } from '../../api/workflow';
import { filesApi } from '../../api/misc';
import type { FileView, RecordView } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, Icon, KeyValue, Modal, Notice, PageHeader, Panel, StatusBadge } from '../../components/ui/Primitives';
import { DetailSkeleton, FormSkeleton, ListSkeleton } from '../../components/ui/Skeleton';
import { DynamicForm, violationsToErrors } from '../../components/forms/DynamicForm';
import { WorkflowTimeline } from '../../components/WorkflowTimeline';
import { t, label } from '../../lib/i18n';
import { fmt } from '../../lib/format';
import { ApiError, errorMessage } from '../../lib/http';

type Tab = 'data' | 'files' | 'workflow' | 'revisions';

function Files({ recordId, canEdit }: { recordId: string; canEdit: boolean }) {
  const files = useRecordFiles(recordId);
  const qc = useQueryClient();
  const tenant = useTenantId();
  const [err, setErr] = useState<string | null>(null);
  const refresh = () => qc.invalidateQueries({ queryKey: ['t', tenant, 'records', recordId, 'files'] });
  const upload = async (file: File) => {
    setErr(null);
    try {
      await filesApi.upload(file, recordId);
      await refresh();
    } catch (e) {
      setErr(errorMessage(e));
    }
  };
  return (
    <div className="space-y-4">
      {canEdit ? (
        <label className="flex items-center justify-center gap-2 h-20 rounded-lg border-2 border-dashed border-outline-variant text-sm text-on-surface-variant cursor-pointer hover:border-secondary">
          <Icon name="upload_file" />Chọn tệp để đính kèm (PDF, ảnh, DOCX, XLSX, TXT; tối đa 20MB)
          <input type="file" className="sr-only" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv,.docx,.xlsx"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f); e.target.value = ''; }} />
        </label>
      ) : null}
      {err ? <ErrorState message={err} /> : null}
      {files.isPending ? <ListSkeleton rows={3} /> : files.error ? <ErrorState message={errorMessage(files.error)} />
        : files.data.length === 0 ? <EmptyState icon="attach_file" title="Chưa có tệp đính kèm" />
        : (
          <ul className="divide-y divide-outline-variant/30 rounded-lg border border-outline-variant/40">
            {files.data.map((f) => (
              <li key={f.id} className="flex items-center gap-3 px-4 py-3">
                <Icon name="draft" className="text-outline" />
                <span className="flex-1 min-w-0">
                  <span className="block text-sm font-medium truncate">{f.name}</span>
                  <span className="block text-xs text-on-surface-variant">{fmt.bytes(f.sizeBytes)} · {f.uploadedBy} · {fmt.relative(f.uploadedAt)}</span>
                </span>
                <Button size="sm" variant="secondary" onClick={() => void filesApi.download(f)}><Icon name="download" className="text-[18px]" /></Button>
              </li>
            ))}
          </ul>
        )}
    </div>
  );
}

function WorkflowTab({ recordId, isOwner }: { recordId: string; isOwner: boolean }) {
  const instances = useRecordInstances(recordId);
  const cancel = useRecordMutation((id: string) => workflowApi.cancel(id, 'Người nộp rút hồ sơ'));
  if (instances.isPending) return <ListSkeleton rows={3} withAvatar={false} />;
  if (instances.error) return <ErrorState message={errorMessage(instances.error)} />;
  if (!instances.data.length) return <EmptyState icon="account_tree" title="Chưa có quy trình" description="Quy trình được khởi tạo sau khi nộp hồ sơ." />;
  return (
    <div className="space-y-6">
      {instances.data.map((i) => (
        <div key={i.id} className="rounded-lg border border-outline-variant/40 p-4">
          <div className="flex flex-wrap items-center gap-3 mb-4">
            <span className="font-medium">{i.definitionCode} <span className="text-xs text-outline">v{i.versionNo}</span></span>
            <StatusBadge status={i.status} map={t.instanceStatus} />
            {i.outcome ? <span className="text-sm text-on-surface-variant">Kết quả: {label(t.outcome, i.outcome)}</span> : null}
            <span className="text-xs text-on-surface-variant ml-auto">Bắt đầu {fmt.dateTime(i.startedAt)}</span>
            {i.status === 'RUNNING' && isOwner ? (
              <Button size="sm" variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate(i.id)}>Rút hồ sơ</Button>
            ) : null}
          </div>
          {i.tasks.filter((x) => x.status === 'OPEN').map((x) => (
            <Notice key={x.id}>Đang chờ <b>{x.name}</b> ({x.assigneeType === 'ROLE' ? `vai trò ${x.assigneeValue}` : x.assigneeValue})
              {x.dueAt ? `, hạn ${fmt.dateTime(x.dueAt)}` : ''}</Notice>
          ))}
          <div className="mt-4"><WorkflowTimeline history={i.history} /></div>
        </div>
      ))}
    </div>
  );
}

function Revisions({ recordId }: { recordId: string }) {
  const revs = useRevisions(recordId);
  if (revs.isPending) return <ListSkeleton rows={4} withAvatar={false} />;
  if (revs.error) return <ErrorState message={errorMessage(revs.error)} />;
  return (
    <table className="w-full text-sm">
      <thead className="text-left text-xs text-on-surface-variant bg-surface-container-low">
        <tr><th className="px-3 h-9 font-medium">#</th><th className="px-3 font-medium">Thay đổi</th><th className="px-3 font-medium">Trạng thái</th><th className="px-3 font-medium">Người thực hiện</th><th className="px-3 font-medium">Thời điểm</th></tr>
      </thead>
      <tbody className="divide-y divide-outline-variant/20">
        {revs.data.slice().reverse().map((r) => (
          <tr key={r.revisionNo} className="h-11">
            <td className="px-3">{r.revisionNo}</td><td className="px-3">{r.changeType}</td>
            <td className="px-3"><StatusBadge status={r.status} /></td><td className="px-3">{r.changedBy}</td>
            <td className="px-3 text-on-surface-variant">{fmt.dateTime(r.changedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DataTab({ record, canEdit }: { record: RecordView; canEdit: boolean }) {
  const version = useFormVersion(record.formCode, record.formVersionNo);
  const files = useRecordFiles(record.id);
  const [draft, setDraft] = useState<Record<string, unknown> | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [msg, setMsg] = useState<string | null>(null);
  const onError = (e: Error) => {
    if (e instanceof ApiError && e.violations.length) setErrors(violationsToErrors(e.violations));
    setMsg(errorMessage(e));
  };
  const save = useRecordMutation((d: Record<string, unknown>) => recordsApi.update(record.id, d, record.version), () => setDraft(null));
  const submit = useRecordMutation(async (d: Record<string, unknown> | null) => {
    const saved = d ? await recordsApi.update(record.id, d, record.version) : record;
    return recordsApi.submit(record.id, saved.version);
  }, () => setDraft(null));

  if (version.isPending) return <FormSkeleton fields={5} />;
  if (version.error) return <ErrorState message={errorMessage(version.error)} />;
  const editing = canEdit && draft !== null;
  const value = draft ?? record.data;
  return (
    <div>
      <DynamicForm schema={version.data.schema} value={value} onChange={setDraft} readOnly={!editing} errors={errors}
        recordId={record.id} files={files.data as FileView[] | undefined} />
      {msg ? <div className="mt-4"><ErrorState message={msg} /></div> : null}
      {canEdit ? (
        <div className="flex justify-end gap-2 mt-6 pt-5 border-t border-outline-variant/30">
          {editing ? (
            <>
              <Button variant="ghost" onClick={() => { setDraft(null); setErrors({}); setMsg(null); }}>Bỏ thay đổi</Button>
              <Button variant="secondary" disabled={save.isPending} onClick={() => { setMsg(null); save.mutate(draft, { onError }); }}>Lưu nháp</Button>
            </>
          ) : <Button variant="secondary" onClick={() => setDraft({ ...record.data })}><Icon name="edit" className="text-[18px] mr-1.5" />Sửa</Button>}
          <Button disabled={submit.isPending} data-testid="submit-existing" onClick={() => { setMsg(null); setErrors({}); submit.mutate(draft, { onError }); }}>
            <Icon name="send" className="text-[18px] mr-1.5" />{record.status === 'RETURNED' ? 'Nộp lại' : 'Nộp hồ sơ'}
          </Button>
        </div>
      ) : null}
    </div>
  );
}

export default function RecordDetail() {
  const { id } = useParams();
  const session = useSession()!;
  const record = useRecord(id);
  const [tab, setTab] = useState<Tab>('data');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const cancel = useRecordMutation(() => recordsApi.cancel(id!), () => setConfirmCancel(false));
  const r = record.data;
  const isOwner = r?.owner === session.subject;
  const canEdit = !!r && isOwner && (r.status === 'DRAFT' || r.status === 'RETURNED');
  const tabs = useMemo(() => [
    ['data', 'Thông tin', 'description'], ['files', 'Tệp đính kèm', 'attach_file'],
    ['workflow', 'Quy trình', 'account_tree'], ['revisions', 'Lịch sử thay đổi', 'history'],
  ] as const, []);

  if (record.isPending) return <><div className="h-[72px]" /><DetailSkeleton /></>;
  if (record.error || !r) return <ErrorState message={errorMessage(record.error)} />;

  return (
    <>
      <PageHeader
        title={r.recordCode}
        subtitle={<span className="inline-flex items-center gap-2"><Link to="/app/records" className="text-secondary">Hồ sơ</Link>/<span>{r.formCode} v{r.formVersionNo}</span><StatusBadge status={r.status} /></span>}
        actions={canEdit ? <Button variant="destructive" onClick={() => setConfirmCancel(true)}>Huỷ hồ sơ</Button> : undefined} />

      {r.status === 'SUBMITTED' ? <div className="mb-4"><Notice>Đang khởi tạo quy trình… trang sẽ tự cập nhật.</Notice></div> : null}
      {r.status === 'RETURNED' ? <div className="mb-4"><Notice tone="warning">Hồ sơ được trả lại để bổ sung. Xem góp ý ở tab Quy trình, sửa rồi nộp lại.</Notice></div> : null}

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_300px] gap-6">
        <Panel padded={false}>
          <div role="tablist" className="flex gap-1 px-3 border-b border-outline-variant/30">
            {tabs.map(([k, text, icon]) => (
              <button key={k} role="tab" type="button" aria-selected={tab === k} onClick={() => setTab(k)}
                className={`flex items-center gap-1.5 px-3 h-11 text-sm border-b-2 -mb-px ${tab === k ? 'border-secondary text-secondary font-medium' : 'border-transparent text-on-surface-variant'}`}>
                <Icon name={icon} className="text-[18px]" />{text}
              </button>
            ))}
          </div>
          <div className="p-5">
            {tab === 'data' ? <DataTab record={r} canEdit={canEdit} />
              : tab === 'files' ? <Files recordId={r.id} canEdit={canEdit} />
              : tab === 'workflow' ? <WorkflowTab recordId={r.id} isOwner={isOwner} />
              : <Revisions recordId={r.id} />}
          </div>
        </Panel>
        <Panel title="Tóm tắt">
          <KeyValue items={[
            ['Người lập', isOwner ? `Tôi (${r.owner})` : r.owner],
            ['Trạng thái', label(t.recordStatus, r.status)],
            ['Tạo lúc', fmt.dateTime(r.createdAt)],
            ['Nộp lúc', fmt.dateTime(r.submittedAt)],
            ['Hoàn tất', fmt.dateTime(r.completedAt)],
            ['Phiên bản dữ liệu', String(r.version)],
          ]} />
        </Panel>
      </div>

      <Modal open={confirmCancel} title="Huỷ hồ sơ?" onClose={() => setConfirmCancel(false)}
        footer={<><Button variant="ghost" onClick={() => setConfirmCancel(false)}>Không</Button>
          <Button variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate(undefined)}>Huỷ hồ sơ</Button></>}>
        <p className="text-sm">Hồ sơ {r.recordCode} sẽ chuyển sang trạng thái Đã huỷ và không thể nộp nữa.</p>
        {cancel.error ? <div className="mt-3"><ErrorState message={errorMessage(cancel.error)} /></div> : null}
      </Modal>
    </>
  );
}
