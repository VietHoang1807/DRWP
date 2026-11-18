import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useWorkflowMutation, useWorkflowVersions, workflowApi } from '../../api/workflow';
import { usePermissions } from '../../api/identity';
import type { WorkflowSpec } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, Icon, Notice, PageHeader, Panel, StatusBadge, Textarea } from '../../components/ui/Primitives';
import { ListSkeleton, SkeletonLine } from '../../components/ui/Skeleton';
import { WorkflowGraph } from '../../components/WorkflowGraph';
import { t, label } from '../../lib/i18n';
import { fmt } from '../../lib/format';
import { ApiError, errorMessage } from '../../lib/http';

export default function WorkflowDesigner() {
  const { code } = useParams();
  const versions = useWorkflowVersions(code);
  const { can } = usePermissions(['workflow']);
  const canConfigure = can('workflow', 'CONFIGURE');
  const [selectedNo, setSelectedNo] = useState<number>();
  const current = useMemo(() => versions.data?.find((v) => v.versionNo === selectedNo) ?? versions.data?.[0], [versions.data, selectedNo]);
  const [json, setJson] = useState('');
  const [spec, setSpec] = useState<WorkflowSpec | null>(null);
  const [parseErr, setParseErr] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null);

  useEffect(() => {
    if (current) {
      setSpec(current.spec);
      setJson(JSON.stringify(current.spec, null, 2));
      setParseErr(null);
    }
  }, [current]);

  const save = useWorkflowMutation((s: WorkflowSpec) => workflowApi.updateDraft(code!, current!.versionNo, s));
  const publish = useWorkflowMutation(() => workflowApi.publish(code!, current!.versionNo));
  const deprecate = useWorkflowMutation(() => workflowApi.deprecate(code!, current!.versionNo));
  const newDraft = useWorkflowMutation(() => workflowApi.newDraft(code!));
  const onErr = (e: Error) => setMessage({
    tone: 'warning',
    text: e instanceof ApiError && e.violations.length ? e.violations.map((v) => `${v.field}: ${v.message}`).join(' · ') : errorMessage(e),
  });

  if (versions.isPending) return <div className="space-y-6"><SkeletonLine className="h-8 w-72" /><Panel><div className="h-[260px]"><ListSkeleton rows={3} withAvatar={false} /></div></Panel></div>;
  if (versions.error) return <ErrorState message={errorMessage(versions.error)} />;
  if (!current || !spec) return <EmptyState title="Quy trình chưa có phiên bản" />;

  const editable = canConfigure && current.status === 'DRAFT';
  const dirty = json !== JSON.stringify(current.spec, null, 2);
  const hasDraft = versions.data.some((v) => v.status === 'DRAFT');

  return (
    <>
      <PageHeader title={`Thiết kế quy trình ${code}`}
        subtitle={<><Link to="/app/workflows" className="text-secondary">Quy trình</Link> / phiên bản {current.versionNo} · {label(t.versionStatus, current.status)}
          {current.publishedAt ? ` · phát hành ${fmt.dateTime(current.publishedAt)}` : ''}</>}
        actions={canConfigure ? (
          <>
            {editable ? (
              <>
                <Button variant="secondary" disabled={!dirty || !!parseErr || save.isPending}
                  onClick={() => { setMessage(null); save.mutate(spec, { onSuccess: () => setMessage({ tone: 'success', text: 'Đã lưu và kiểm tra hợp lệ.' }), onError: onErr }); }}>Lưu nháp</Button>
                <Button disabled={dirty || publish.isPending}
                  onClick={() => publish.mutate(undefined, { onSuccess: () => setMessage({ tone: 'success', text: 'Đã phát hành. Hồ sơ nộp từ bây giờ dùng phiên bản này.' }), onError: onErr })}>
                  <Icon name="publish" className="text-[18px] mr-1.5" />Phát hành
                </Button>
              </>
            ) : null}
            {current.status === 'PUBLISHED' ? <Button variant="destructive" onClick={() => deprecate.mutate(undefined, { onError: onErr })}>Ngừng dùng</Button> : null}
            {!hasDraft ? <Button variant="secondary" onClick={() => newDraft.mutate(undefined, { onSuccess: (v) => setSelectedNo(v.versionNo), onError: onErr })}>
              <Icon name="content_copy" className="text-[18px] mr-1.5" />Tạo phiên bản mới</Button> : null}
          </>
        ) : undefined} />

      {message ? <div className="mb-4"><Notice tone={message.tone}>{message.text}</Notice></div> : null}

      <div className="grid grid-cols-1 xl:grid-cols-[200px_1fr] gap-6 items-start">
        <Panel title="Phiên bản">
          <ul className="space-y-1">
            {versions.data.map((v) => (
              <li key={v.id}>
                <button type="button" onClick={() => setSelectedNo(v.versionNo)}
                  className={`w-full flex items-center justify-between rounded-lg px-3 py-2 text-sm ${v.versionNo === current.versionNo ? 'bg-secondary-fixed/50' : 'hover:bg-surface-container-low'}`}>
                  <span>v{v.versionNo}</span><StatusBadge status={v.status} map={t.versionStatus} />
                </button>
              </li>
            ))}
          </ul>
        </Panel>
        <div className="space-y-6 min-w-0">
          <Panel title="Sơ đồ" actions={<span className="text-xs text-on-surface-variant">Nét đứt: nhánh có điều kiện</span>}>
            <WorkflowGraph spec={spec} />
          </Panel>
          <div className="grid grid-cols-1 2xl:grid-cols-2 gap-6">
            <Panel title="Định nghĩa (JSON)">
              <Textarea value={json} readOnly={!editable} spellCheck={false} className="font-mono text-xs min-h-[420px]"
                onChange={(e) => {
                  setJson(e.target.value);
                  try { setSpec(JSON.parse(e.target.value) as WorkflowSpec); setParseErr(null); } catch { setParseErr('JSON chưa hợp lệ'); }
                }} />
              {parseErr ? <p className="text-xs text-error mt-2">{parseErr}</p> : <p className="text-xs text-on-surface-variant mt-2">Máy chủ kiểm tra tĩnh khi lưu: trạng thái đạt tới được, có kết thúc, nhánh mặc định, không vòng lặp tự động.</p>}
            </Panel>
            <Panel title="Các bước chuyển" padded={false}>
              <table className="w-full text-sm">
                <thead className="bg-surface-container-low text-xs text-left text-on-surface-variant">
                  <tr><th className="px-4 h-9 font-medium">Từ</th><th className="px-4 font-medium">Thao tác</th><th className="px-4 font-medium">Đến</th><th className="px-4 font-medium">Quyền / Điều kiện</th></tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {spec.transitions.map((x) => (
                    <tr key={`${x.from}-${x.code}`} className="h-11">
                      <td className="px-4">{x.from}</td><td className="px-4 font-medium">{x.label ?? x.code}</td><td className="px-4">{x.to}</td>
                      <td className="px-4 text-xs text-on-surface-variant">
                        {x.permission ? label(t.action, x.permission) : 'Tự động'}{x.commentRequired ? ' · cần ý kiến' : ''}{x.guard ? ` · ${JSON.stringify(x.guard)}` : ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>
          </div>
        </div>
      </div>
    </>
  );
}
