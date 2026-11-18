import { useState } from 'react';
import type { FieldDef, FileView, FormSchema } from '../../api/types';
import { filesApi } from '../../api/misc';
import { Field, Icon, Select, Textarea } from '../ui/Primitives';
import { Input } from '../ui/Input';
import { errorMessage } from '../../lib/http';
import { fmt } from '../../lib/format';

type Data = Record<string, unknown>;

interface Props {
  schema: FormSchema;
  value: Data;
  onChange?: (next: Data) => void;
  errors?: Record<string, string>;
  readOnly?: boolean;
  /** Có recordId thì trường FILE cho phép tải tệp lên gắn với hồ sơ */
  recordId?: string;
  files?: FileView[];
  onFileUploaded?: (f: FileView) => void;
}

/** datetime-local (không có múi giờ) <-> ISO có offset mà backend yêu cầu */
function toLocalInput(iso: unknown) {
  if (typeof iso !== 'string' || !iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function fromLocalInput(v: string) {
  if (!v) return undefined;
  const d = new Date(v);
  const off = -d.getTimezoneOffset();
  const sign = off >= 0 ? '+' : '-';
  const pad = (n: number) => String(Math.floor(Math.abs(n))).padStart(2, '0');
  return `${v}:00${sign}${pad(off / 60)}:${pad(off % 60)}`;
}

export function displayValue(f: FieldDef, v: unknown, files?: FileView[]) {
  if (v === undefined || v === null || v === '') return '—';
  switch (f.type) {
    case 'BOOLEAN':
      return v ? 'Có' : 'Không';
    case 'DATE':
      return fmt.date(String(v));
    case 'DATETIME':
      return fmt.dateTime(String(v));
    case 'MULTISELECT':
      return Array.isArray(v) ? v.join(', ') : String(v);
    case 'FILE':
      return Array.isArray(v) ? v.map((id) => files?.find((x) => x.id === id)?.name ?? String(id).slice(0, 8)).join(', ') : '—';
    case 'NUMBER':
    case 'INTEGER':
      return typeof v === 'number' ? fmt.number(v) : String(v);
    default:
      return String(v);
  }
}

function FileField({ f, value, recordId, files, onChange, onFileUploaded, readOnly }:
  { f: FieldDef; value: unknown; recordId?: string; files?: FileView[]; onChange: (v: unknown) => void;
    onFileUploaded?: (f: FileView) => void; readOnly?: boolean }) {
  const ids = Array.isArray(value) ? (value as string[]) : [];
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const upload = async (file: File) => {
    setBusy(true);
    setErr(null);
    try {
      const saved = await filesApi.upload(file, recordId);
      onFileUploaded?.(saved);
      onChange([...ids, saved.id]);
    } catch (e) {
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="space-y-2">
      {ids.length ? (
        <ul className="space-y-1">
          {ids.map((id) => {
            const meta = files?.find((x) => x.id === id);
            return (
              <li key={id} className="flex items-center gap-2 text-sm rounded border border-outline-variant/50 px-2.5 py-1.5">
                <Icon name="attach_file" className="text-[18px] text-outline" />
                <span className="flex-1 truncate">{meta?.name ?? id}</span>
                {meta ? <button type="button" className="text-secondary text-xs" onClick={() => void filesApi.download(meta)}>Tải về</button> : null}
                {!readOnly ? (
                  <button type="button" aria-label="Bỏ tệp" className="text-outline" onClick={() => onChange(ids.filter((x) => x !== id))}>
                    <Icon name="close" className="text-[16px]" />
                  </button>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}
      {!readOnly ? (
        recordId ? (
          <label className={`inline-flex items-center gap-2 text-sm font-medium text-secondary cursor-pointer ${busy ? 'opacity-50' : ''}`}>
            <Icon name="upload" className="text-[18px]" />{busy ? 'Đang tải lên…' : 'Thêm tệp'}
            <input type="file" className="sr-only" disabled={busy} aria-label={`Tải tệp cho ${f.label ?? f.key}`}
              accept=".pdf,.png,.jpg,.jpeg,.txt,.csv,.docx,.xlsx"
              onChange={(e) => { const file = e.target.files?.[0]; if (file) void upload(file); e.target.value = ''; }} />
          </label>
        ) : <p className="text-xs text-on-surface-variant">Lưu nháp trước để đính kèm tệp vào hồ sơ.</p>
      ) : null}
      {err ? <p className="text-xs text-error">{err}</p> : null}
    </div>
  );
}

function Control({ f, value, set, readOnly }: { f: FieldDef; value: unknown; set: (v: unknown) => void; readOnly?: boolean }) {
  const id = `f-${f.key}`;
  switch (f.type) {
    case 'TEXTAREA':
      return <Textarea id={id} disabled={readOnly} value={String(value ?? '')} maxLength={f.maxLength}
        onChange={(e) => set(e.target.value || undefined)} />;
    case 'NUMBER':
    case 'INTEGER':
      return <Input id={id} type="number" disabled={readOnly} value={value === undefined || value === null ? '' : String(value)}
        step={f.type === 'INTEGER' ? 1 : 'any'} min={f.min} max={f.max}
        onChange={(e) => set(e.target.value === '' ? undefined : Number(e.target.value))} />;
    case 'BOOLEAN':
      return (
        <label className="flex items-center gap-2 text-sm h-[38px]">
          <input id={id} type="checkbox" disabled={readOnly} checked={value === true} onChange={(e) => set(e.target.checked)} />
          {f.description ?? 'Có'}
        </label>
      );
    case 'DATE':
      return <Input id={id} type="date" disabled={readOnly} value={String(value ?? '')} onChange={(e) => set(e.target.value || undefined)} />;
    case 'DATETIME':
      return <Input id={id} type="datetime-local" disabled={readOnly} value={toLocalInput(value)} onChange={(e) => set(fromLocalInput(e.target.value))} />;
    case 'SELECT':
      return (
        <Select id={id} disabled={readOnly} value={String(value ?? '')} onChange={(e) => set(e.target.value || undefined)}>
          <option value="">— Chọn —</option>
          {f.options?.map((o) => <option key={o} value={o}>{o}</option>)}
        </Select>
      );
    case 'MULTISELECT': {
      const arr = Array.isArray(value) ? (value as string[]) : [];
      return (
        <div className="flex flex-wrap gap-2" role="group" aria-labelledby={id}>
          {f.options?.map((o) => (
            <label key={o} className={`px-3 h-8 inline-flex items-center gap-1.5 rounded-full border text-sm ${arr.includes(o) ? 'border-secondary bg-secondary-fixed/50' : 'border-outline-variant'}`}>
              <input type="checkbox" className="sr-only" disabled={readOnly} checked={arr.includes(o)}
                onChange={(e) => set(e.target.checked ? [...arr, o] : arr.filter((x) => x !== o))} />
              {arr.includes(o) ? <Icon name="check" className="text-[16px]" /> : null}{o}
            </label>
          ))}
        </div>
      );
    }
    default:
      return <Input id={id} type={f.type === 'EMAIL' ? 'email' : 'text'} disabled={readOnly} value={String(value ?? '')}
        maxLength={f.maxLength} onChange={(e) => set(e.target.value || undefined)} />;
  }
}

export function DynamicForm({ schema, value, onChange, errors = {}, readOnly, recordId, files, onFileUploaded }: Props) {
  const set = (key: string) => (v: unknown) => {
    const next = { ...value };
    if (v === undefined || (Array.isArray(v) && v.length === 0)) delete next[key];
    else next[key] = v;
    onChange?.(next);
  };
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-5">
      {schema.fields.map((f) => (
        <div key={f.key} className={f.type === 'TEXTAREA' || f.type === 'FILE' || f.type === 'MULTISELECT' ? 'md:col-span-2' : ''}>
          <Field label={f.label ?? f.key} htmlFor={`f-${f.key}`} required={f.required} error={errors[f.key]}
            hint={f.type === 'BOOLEAN' ? undefined : f.description}>
            {readOnly && f.type !== 'FILE' ? (
              <p className="text-sm min-h-[38px] flex items-center">{displayValue(f, value[f.key], files)}</p>
            ) : f.type === 'FILE' ? (
              <FileField f={f} value={value[f.key]} recordId={recordId} files={files} readOnly={readOnly}
                onChange={set(f.key)} onFileUploaded={onFileUploaded} />
            ) : (
              <Control f={f} value={value[f.key]} set={set(f.key)} readOnly={readOnly} />
            )}
          </Field>
        </div>
      ))}
    </div>
  );
}

/** Lỗi 422 từ backend -> map field -> thông điệp */
export function violationsToErrors(violations: { field: string; message: string }[]) {
  const out: Record<string, string> = {};
  for (const v of violations) out[v.field] ??= v.message;
  return out;
}
