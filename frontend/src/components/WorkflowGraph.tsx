import { memo, useMemo } from 'react';
import type { WorkflowSpec } from '../api/types';

const W = 168, H = 56, GX = 84, GY = 34;

/** Sơ đồ trạng thái: xếp cột theo khoảng cách BFS từ trạng thái đầu. Chỉ đọc. */
export const WorkflowGraph = memo(function WorkflowGraph({ spec, highlight }: { spec: WorkflowSpec; highlight?: string }) {
  const layout = useMemo(() => {
    const level = new Map<string, number>();
    const queue = [spec.initialState];
    level.set(spec.initialState, 0);
    while (queue.length) {
      const s = queue.shift()!;
      for (const t of spec.transitions) {
        if (t.from === s && !level.has(t.to)) {
          level.set(t.to, level.get(s)! + 1);
          queue.push(t.to);
        }
      }
    }
    const cols = new Map<number, string[]>();
    for (const st of spec.states) {
      const l = level.get(st.code) ?? 0;
      cols.set(l, [...(cols.get(l) ?? []), st.code]);
    }
    const pos = new Map<string, { x: number; y: number }>();
    let maxRows = 1;
    cols.forEach((codes, l) => {
      maxRows = Math.max(maxRows, codes.length);
      codes.forEach((c, i) => pos.set(c, { x: 16 + l * (W + GX), y: 16 + i * (H + GY) }));
    });
    return { pos, width: 32 + cols.size * (W + GX) - GX, height: 32 + maxRows * (H + GY) - GY };
  }, [spec]);

  return (
    <div className="overflow-x-auto">
      <svg width={layout.width} height={layout.height} role="img" aria-label="Sơ đồ quy trình" className="block">
        <defs>
          <marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M0 0L10 5L0 10z" fill="#757684" />
          </marker>
        </defs>
        {spec.transitions.map((t, i) => {
          const a = layout.pos.get(t.from), b = layout.pos.get(t.to);
          if (!a || !b) return null;
          const back = b.x <= a.x;
          const x1 = a.x + W, y1 = a.y + H / 2 + ((i % 3) - 1) * 8;
          const x2 = back ? b.x + W / 2 : b.x, y2 = back ? b.y + H : b.y + H / 2;
          const d = back
            ? `M${x1} ${y1} C${x1 + 40} ${y1 + 80}, ${x2} ${y2 + 60}, ${x2} ${y2}`
            : `M${x1} ${y1} C${x1 + GX / 2} ${y1}, ${x2 - GX / 2} ${y2}, ${x2} ${y2}`;
          return (
            <g key={`${t.from}-${t.code}`}>
              <path d={d} fill="none" stroke={t.guard ? '#0051d5' : '#757684'} strokeDasharray={t.guard ? '5 4' : undefined} strokeWidth="1.4" markerEnd="url(#arr)" />
              <text x={(x1 + x2) / 2} y={(y1 + y2) / 2 - 6} textAnchor="middle" fontSize="11" fill="#444653">{t.label ?? t.code}</text>
            </g>
          );
        })}
        {spec.states.map((s) => {
          const p = layout.pos.get(s.code);
          if (!p) return null;
          const end = s.type === 'END';
          const fill = end ? (s.outcome === 'APPROVED' ? '#ECFDF5' : s.outcome === 'RETURNED' ? '#FFFBEB' : '#FEF2F2')
            : s.type === 'DECISION' ? '#eaedff' : '#ffffff';
          const stroke = highlight === s.code ? '#0051d5' : end ? '#a7b0c0' : s.type === 'DECISION' ? '#316bf3' : '#1e40af';
          return (
            <g key={s.code} transform={`translate(${p.x} ${p.y})`}>
              {s.type === 'DECISION'
                ? <polygon points={`${W / 2},0 ${W},${H / 2} ${W / 2},${H} 0,${H / 2}`} fill={fill} stroke={stroke} strokeWidth="1.5" />
                : <rect width={W} height={H} rx={end ? H / 2 : 10} fill={fill} stroke={stroke} strokeWidth={highlight === s.code ? 3 : 1.5} />}
              <text x={W / 2} y={H / 2 - 4} textAnchor="middle" fontSize="12" fontWeight="600" fill="#131b2e">{s.name ?? s.code}</text>
              <text x={W / 2} y={H / 2 + 12} textAnchor="middle" fontSize="10" fill="#757684">
                {s.type === 'TASK' ? `${s.assignment?.type ?? ''} ${s.assignment?.value ?? ''}`.trim() : s.type === 'END' ? s.outcome : 'rẽ nhánh'}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
});
