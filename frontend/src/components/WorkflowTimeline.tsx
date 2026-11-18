import type { HistoryRow } from '../api/types';
import { Icon } from './ui/Primitives';
import { fmt } from '../lib/format';

export function WorkflowTimeline({ history }: { history: HistoryRow[] }) {
  if (!history.length) return <p className="text-sm text-on-surface-variant">Chưa có bước nào.</p>;
  return (
    <ol className="relative ml-3 border-l border-outline-variant/60 space-y-5">
      {history.map((h) => (
        <li key={h.seq} className="pl-5 relative">
          <span className={`absolute -left-[9px] top-0.5 w-4 h-4 rounded-full border-2 border-surface-container-lowest ${
            h.automatic ? 'bg-outline-variant' : 'bg-secondary'}`} />
          <p className="text-sm">
            {h.fromState ? <><span className="font-medium">{h.fromState}</span><Icon name="arrow_forward" className="text-[14px] mx-1 align-middle text-outline" /></> : 'Bắt đầu tại '}
            <span className="font-medium">{h.toState}</span>
            {h.transitionCode ? <span className="ml-2 text-xs px-1.5 py-0.5 rounded bg-surface-container">{h.transitionCode}</span> : null}
          </p>
          <p className="text-xs text-on-surface-variant mt-0.5">
            {h.automatic ? 'Tự động' : h.actor} · {fmt.dateTime(h.occurredAt)}
          </p>
          {h.comment ? <p className="mt-1.5 text-sm rounded bg-surface-container-low px-3 py-2">“{h.comment}”</p> : null}
        </li>
      ))}
    </ol>
  );
}
