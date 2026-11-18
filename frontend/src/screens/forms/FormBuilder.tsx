import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { metadataApi, useFormMutation, useFormVersions } from '../../api/metadata';
import { usePermissions } from '../../api/identity';
import type { FieldDef, FieldType, FormSchema, FormVersionView, Violation } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { EmptyState, ErrorState, Field, Icon, Notice, PageHeader, Panel, Select, StatusBadge, Textarea } from '../../components/ui/Primitives';
import { FormSkeleton, ListSkeleton } from '../../components/ui/Skeleton';
import { DynamicForm } from '../../components/forms/DynamicForm';
import { t } from '../../lib/i18n';
import { fmt } from '../../lib/format';
import { ApiError, errorMessage } from '../../lib/http';

const TYPES = Object.keys(t.fieldType) as FieldType[];
const KEY = /^[a-z][a-zA-Z0-9_]{0,49}$/;

function FieldEditor({ f, onChange, onRemove, onMove, readOnly, first, last }: {
  f: FieldDef; onChange: (f: FieldDef) => void; onRemove: () => void; onMove: (d: -1 | 1) => void;
  readOnly: boolean; first: boolean; last: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [condText, setCondText] = useState(f.requiredIf ? JSON.stringify(f.requiredIf) : '');
  const [condErr, setCondErr] = useState<string | null>(null);
  const set = <K extends keyof FieldDef>(k: K, v: FieldDef[K]) => {
    const next = { ...f, [k]: v };
    if (v === undefined || v === '' || v === false) delete next[k];
    onChange(next);
  };
  const num = (v: string) => (v === '' ? undefined : Number(v));
  const hasOptions = f.type === 'SELECT' || f.type === 'MULTISELECT';
  const isText = f.type === 'TEXT' || f.type === 'TEXTAREA' || f.type === 'EMAIL';
  const isNum = f.type === 'NUMBER' || f.type === 'INTEGER';

  return (
    <li className="rounded-lg border border-outline-variant/50 bg-surface-container-lowest">
      <div className="flex items-center gap-2 px-3 h-12">
        <button type="button" onClick={() => setOpen((o) => !o)} className="flex-1 flex items-center gap-2 text-left min-w-0" aria-expanded={open}>
          <Icon name={open ? 'expand_less' : 'expand_more'} className="text-[18px] text-outline" />
          <span className="text-sm font-medium truncate">{f.label || f.key}</span>
          <span className="text-xs text-outline">{f.key} · {t.fieldType[f.type]}</span>
          {f.required ? <span className="text-xs text-error">bắt buộc</span> : null}
        </button>
        {!readOnly ? (
          <>
            <button type="button" aria-label="Lên" disabled={first} onClick={() => onMove(-1)} className="text-outline disabled:opacity-30"><Icon name="arrow_upward" className="text-[18px]" /></button>
            <button type="button" aria-label="Xuống" disabled={last} onClick={() => onMove(1)} className="text-outline disabled:opacity-30"><Icon name="arrow_downward" className="text-[18px]" /></button>
            <button type="button" aria-label="Xoá trường" onClick={onRemove} className="text-error"><Icon name="delete" className="text-[18px]" /></button>
          </>
        ) : null}
      </div>
      {open ? (
        <fieldset disabled={readOnly} className="grid grid-cols-2 gap-3 p-3 pt-1 border-t border-outline-variant/30">
          <Field label="Khóa (key)" error={KEY.test(f.key) ? undefined : 'a-z, số, _; bắt đầu bằng chữ thường'}>
            <Input value={f.key} onChange={(e) => set('key', e.target.value)} />
          </Field>
          <Field label="Nhãn"><Input value={f.label ?? ''} onChange={(e) => set('label', e.target.value)} /></Field>
          <Field label="Kiểu">
            <Select value={f.type} onChange={(e) => onChange({ key: f.key, label: f.label, type: e.target.value as FieldType, required: f.required })}>
              {TYPES.map((x) => <option key={x} value={x}>{t.fieldType[x]}</option>)}
            </Select>
          </Field>
          <div className="flex flex-col justify-end gap-1.5 text-sm pb-1">
            <label className="flex items-center gap-2"><input type="checkbox" checked={!!f.required} onChange={(e) => set('required', e.target.checked)} />Bắt buộc</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={!!f.filterable} onChange={(e) => set('filterable', e.target.checked)} />Cho phép lọc</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={!!f.sensitive} onChange={(e) => set('sensitive', e.target.checked)} />Nhạy cảm (ẩn ở danh sách)</label>
          </div>
          {hasOptions ? (
            <div className="col-span-2">
              <Field label="Lựa chọn (mỗi dòng một giá trị)">
                <Textarea value={(f.options ?? []).join('\n')} onChange={(e) => set('options', e.target.value.split('\n').map((s) => s.trim()).filter(Boolean))} />
              </Field>
            </div>
          ) : null}
          {isText ? (
            <>
              <Field label="Độ dài tối thiểu"><Input type="number" value={f.minLength ?? ''} onChange={(e) => set('minLength', num(e.target.value))} /></Field>
              <Field label="Độ dài tối đa"><Input type="number" value={f.maxLength ?? ''} onChange={(e) => set('maxLength', num(e.target.value))} /></Field>
              {f.type !== 'EMAIL' ? (
                <div className="col-span-2"><Field label="Mẫu (regex)" hint="Không hỗ trợ backreference/lookaround; kiểm tra có giới hạn thời gian.">
                  <Input value={f.pattern ?? ''} onChange={(e) => set('pattern', e.target.value)} placeholder="^[0-9]{12}$" />
                </Field></div>
              ) : null}
            </>
          ) : null}
          {isNum ? (
            <>
              <Field label="Giá trị nhỏ nhất"><Input type="number" value={f.min ?? ''} onChange={(e) => set('min', num(e.target.value))} /></Field>
              <Field label="Giá trị lớn nhất"><Input type="number" value={f.max ?? ''} onChange={(e) => set('max', num(e.target.value))} /></Field>
            </>
          ) : null}
          <div className="col-span-2"><Field label="Mô tả / gợi ý"><Input value={f.description ?? ''} onChange={(e) => set('description', e.target.value)} /></Field></div>
          <div className="col-span-2">
            <Field label="Bắt buộc khi (requiredIf, JSON)" error={condErr ?? undefined} hint='Ví dụ: {"field":"days","op":"gt","value":3}'>
              <Input value={condText} onChange={(e) => setCondText(e.target.value)} onBlur={() => {
                if (!condText.trim()) { setCondErr(null); set('requiredIf', undefined); return; }
                try { set('requiredIf', JSON.parse(condText)); setCondErr(null); } catch { setCondErr('JSON không hợp lệ'); }
              }} />
            </Field>
          </div>
        </fieldset>
      ) : null}
    </li>
  );
}

function VersionList({ versions, selected, onSelect }: { versions: FormVersionView[]; selected?: number; onSelect: (n: number) => void }) {
  return (
    <ul className="space-y-1">
      {versions.map((v) => (
        <li key={v.id}>
          <button type="button" onClick={() => onSelect(v.versionNo)}
            className={`w-full flex items-center justify-between rounded-lg px-3 py-2 text-sm ${v.versionNo === selected ? 'bg-secondary-fixed/50' : 'hover:bg-surface-container-low'}`}>
            <span>Phiên bản {v.versionNo}</span>
            <StatusBadge status={v.status} map={t.versionStatus} />
          </button>
        </li>
      ))}
    </ul>
  );
}

export default function FormBuilder() {
  const { code } = useParams();
  const versions = useFormVersions(code);
  const { can } = usePermissions(['metadata']);
  const canConfigure = can('metadata', 'CONFIGURE');
  const [selectedNo, setSelectedNo] = useState<number>();
  const current = useMemo(() => versions.data?.find((v) => v.versionNo === selectedNo) ?? versions.data?.[0], [versions.data, selectedNo]);
  const [schema, setSchema] = useState<FormSchema | null>(null);
  const [mode, setMode] = useState<'visual' | 'json'>('visual');
  const [json, setJson] = useState('');
  const [preview, setPreview] = useState<Record<string, unknown>>({});
  const [violations, setViolations] = useState<Violation[] | null>(null);
  const [message, setMessage] = useState<{ tone: 'success' | 'warning'; text: string } | null>(null);

  // nạp schema của phiên bản đang xem vào trình soạn
  useEffect(() => {
    if (current) {
      setSchema(current.schema);
      setJson(JSON.stringify(current.schema, null, 2));
      setViolations(null);
    }
  }, [current]);

  const editable = canConfigure && current?.status === 'DRAFT';
  const save = useFormMutation((s: FormSchema) => metadataApi.updateDraft(code!, current!.versionNo, s));
  const publish = useFormMutation(() => metadataApi.publish(code!, current!.versionNo));
  const deprecate = useFormMutation(() => metadataApi.deprecate(code!, current!.versionNo));
  const newDraft = useFormMutation(() => metadataApi.newDraft(code!));
  const validate = useFormMutation((d: Record<string, unknown>) => metadataApi.validate(code!, current!.versionNo, d, true));

  const onErr = (e: Error) => setMessage({
    tone: 'warning',
    text: e instanceof ApiError && e.violations.length ? e.violations.map((v) => `${v.field}: ${v.message}`).join(' · ') : errorMessage(e),
  });

  const update = (fields: FieldDef[]) => {
    const s = { fields };
    setSchema(s);
    setJson(JSON.stringify(s, null, 2));
  };

  if (versions.isPending) return <div className="grid grid-cols-1 xl:grid-cols-[1fr_1fr] gap-6"><Panel><ListSkeleton rows={5} withAvatar={false} /></Panel><Panel><FormSkeleton /></Panel></div>;
  if (versions.error) return <ErrorState message={errorMessage(versions.error)} />;
  if (!current || !schema) return <EmptyState title="Biểu mẫu chưa có phiên bản" />;

  const dirty = JSON.stringify(schema) !== JSON.stringify(current.schema);
  const fields = schema.fields;
  const hasDraft = versions.data.some((v) => v.status === 'DRAFT');

  return (
    <>
      <PageHeader title={`Thiết kế biểu mẫu ${code}`}
        subtitle={<><Link to="/app/forms" className="text-secondary">Biểu mẫu</Link> / phiên bản {current.versionNo} · {t.versionStatus[current.status]}
          {current.publishedAt ? ` · phát hành ${fmt.dateTime(current.publishedAt)} bởi ${current.publishedBy}` : ''}</>}
        actions={canConfigure ? (
          <>
            {editable ? (
              <>
                <Button variant="secondary" disabled={!dirty || save.isPending}
                  onClick={() => { setMessage(null); save.mutate(schema, { onSuccess: () => setMessage({ tone: 'success', text: 'Đã lưu bản nháp.' }), onError: onErr }); }}>Lưu nháp</Button>
                <Button disabled={dirty || publish.isPending} title={dirty ? 'Lưu nháp trước khi phát hành' : undefined}
                  onClick={() => { setMessage(null); publish.mutate(undefined, { onSuccess: () => setMessage({ tone: 'success', text: 'Đã phát hành. Phiên bản này không thể sửa nữa.' }), onError: onErr }); }}>
                  <Icon name="publish" className="text-[18px] mr-1.5" />Phát hành
                </Button>
              </>
            ) : null}
            {current.status === 'PUBLISHED' ? <Button variant="destructive" disabled={deprecate.isPending} onClick={() => deprecate.mutate(undefined, { onError: onErr })}>Ngừng dùng</Button> : null}
            {!hasDraft ? <Button variant="secondary" disabled={newDraft.isPending} onClick={() => newDraft.mutate(undefined, { onSuccess: (v) => setSelectedNo(v.versionNo), onError: onErr })}>
              <Icon name="content_copy" className="text-[18px] mr-1.5" />Tạo phiên bản mới</Button> : null}
          </>
        ) : undefined} />

      {message ? <div className="mb-4"><Notice tone={message.tone}>{message.text}</Notice></div> : null}
      {!editable && canConfigure && current.status !== 'DRAFT' ? (
        <div className="mb-4"><Notice>Phiên bản đã phát hành là bất biến. Hồ sơ đang dùng phiên bản này không bị ảnh hưởng khi bạn phát hành phiên bản mới.</Notice></div>
      ) : null}

      <div className="grid grid-cols-1 xl:grid-cols-[200px_1fr_1fr] gap-6 items-start">
        <Panel title="Phiên bản"><VersionList versions={versions.data} selected={current.versionNo} onSelect={setSelectedNo} /></Panel>

        <Panel title={`Trường dữ liệu (${fields.length})`} actions={
          <div className="flex rounded-lg border border-outline-variant/60 p-0.5 text-xs">
            {(['visual', 'json'] as const).map((m) => (
              <button key={m} type="button" onClick={() => setMode(m)} className={`px-2.5 h-7 rounded ${mode === m ? 'bg-primary text-white' : ''}`}>{m === 'visual' ? 'Trực quan' : 'JSON'}</button>
            ))}
          </div>}>
          {mode === 'visual' ? (
            <>
              <ul className="space-y-2">
                {fields.map((f, i) => (
                  <FieldEditor key={i} f={f} readOnly={!editable} first={i === 0} last={i === fields.length - 1}
                    onChange={(nf) => update(fields.map((x, j) => (j === i ? nf : x)))}
                    onRemove={() => update(fields.filter((_, j) => j !== i))}
                    onMove={(d) => { const next = fields.slice(); [next[i], next[i + d]] = [next[i + d], next[i]]; update(next); }} />
                ))}
              </ul>
              {editable ? (
                <Button variant="secondary" className="mt-3 w-full" onClick={() => update([...fields, { key: `field${fields.length + 1}`, label: 'Trường mới', type: 'TEXT' }])}>
                  <Icon name="add" className="text-[18px] mr-1.5" />Thêm trường
                </Button>
              ) : null}
            </>
          ) : (
            <Textarea value={json} readOnly={!editable} spellCheck={false} className="font-mono text-xs min-h-[480px]"
              onChange={(e) => { setJson(e.target.value); try { setSchema(JSON.parse(e.target.value) as FormSchema); } catch { /* chờ JSON hợp lệ */ } }} />
          )}
        </Panel>

        <Panel title="Xem trước" actions={current.status !== 'DRAFT' || !dirty ? (
          <Button size="sm" variant="secondary" disabled={validate.isPending}
            onClick={() => validate.mutate(preview, { onSuccess: (r) => setViolations(r.violations), onError: onErr })}>Kiểm tra dữ liệu</Button>
        ) : undefined}>
          <DynamicForm schema={schema} value={preview} onChange={setPreview}
            errors={Object.fromEntries((violations ?? []).map((v) => [v.field, v.message]))} />
          {violations ? (
            <div className="mt-4">{violations.length === 0 ? <Notice tone="success">Dữ liệu hợp lệ với phiên bản {current.versionNo}.</Notice>
              : <Notice tone="warning">{violations.length} lỗi theo quy tắc của máy chủ.</Notice>}</div>
          ) : null}
        </Panel>
      </div>
    </>
  );
}
