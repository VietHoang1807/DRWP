import { Link } from 'react-router';
import { useSession } from '../auth/session';
import { useInbox } from '../api/workflow';
import { useRecords } from '../api/records';
import { useNotifications, useUnreadCount } from '../api/misc';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, Icon, PageHeader, Panel, StatusBadge } from '../components/ui/Primitives';
import { ListSkeleton, SkeletonLine } from '../components/ui/Skeleton';
import { fmt } from '../lib/format';
import { errorMessage } from '../lib/http';

function Stat({ label, value, icon, to, loading }: { label: string; value?: number; icon: string; to: string; loading: boolean }) {
  return (
    <Link to={to} className="group p-5 rounded-xl border border-outline-variant/40 bg-surface-container-lowest hover:border-secondary/50 transition-colors h-[118px] flex flex-col justify-between">
      <span className="flex items-center justify-between text-sm text-on-surface-variant">
        {label}<Icon name={icon} className="text-[20px] text-outline group-hover:text-secondary" />
      </span>
      {loading ? <SkeletonLine className="h-8 w-14" /> : <span className="text-3xl font-semibold tracking-tight">{fmt.number(value ?? 0)}</span>}
    </Link>
  );
}

export default function Dashboard() {
  const session = useSession()!;
  const me = session.subject;
  // các khối độc lập tải song song, mỗi khối có skeleton riêng
  const inbox = useInbox(0, 5);
  const mine = useRecords({ createdBy: me, size: 5, sort: 'UPDATED_AT', desc: true });
  const inProgress = useRecords({ createdBy: me, status: 'IN_PROGRESS', size: 1 });
  const returned = useRecords({ createdBy: me, status: 'RETURNED', size: 1 });
  const unread = useUnreadCount();
  const notes = useNotifications(false, 0, 5);

  return (
    <>
      <PageHeader title={`Xin chào, ${session.username}`} subtitle="Việc cần làm và hồ sơ của bạn trong đơn vị này."
        actions={<Link to="/app/records/new"><Button><Icon name="add" className="text-[18px] mr-1.5" />Lập hồ sơ</Button></Link>} />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <Stat label="Việc chờ tôi xử lý" icon="task_alt" to="/app/tasks" value={inbox.data?.total} loading={inbox.isPending} />
        <Stat label="Hồ sơ của tôi đang xử lý" icon="hourglass_top" to="/app/records?mine=1&status=IN_PROGRESS" value={inProgress.data?.total} loading={inProgress.isPending} />
        <Stat label="Bị trả lại cần bổ sung" icon="undo" to="/app/records?mine=1&status=RETURNED" value={returned.data?.total} loading={returned.isPending} />
        <Stat label="Thông báo chưa đọc" icon="notifications" to="/app/notifications" value={unread.data?.unread} loading={unread.isPending} />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <Panel title="Việc mới nhất" padded={false} actions={<Link to="/app/tasks" className="text-sm text-secondary">Xem tất cả</Link>}>
          {inbox.isPending ? <ListSkeleton rows={4} /> : inbox.error ? <div className="p-4"><ErrorState message={errorMessage(inbox.error)} /></div>
            : inbox.data.items.length === 0 ? <EmptyState icon="done_all" title="Không có việc nào đang chờ" description="Việc được giao sẽ xuất hiện ở đây." />
            : (
              <ul className="divide-y divide-outline-variant/20">
                {inbox.data.items.map((t) => (
                  <li key={t.id}>
                    <Link to={`/app/tasks?task=${t.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-surface-container-low">
                      <span className="w-9 h-9 rounded-full bg-secondary-fixed/60 text-secondary flex items-center justify-center"><Icon name="assignment" className="text-[18px]" /></span>
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-medium truncate">{t.name} · {t.recordCode}</span>
                        <span className="block text-xs text-on-surface-variant">{t.definitionCode} · giao {fmt.relative(t.createdAt)}</span>
                      </span>
                      {t.dueAt ? <span className="text-xs text-[#92400E]">Hạn {fmt.dateTime(t.dueAt)}</span> : null}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
        </Panel>

        <Panel title="Hồ sơ gần đây của tôi" padded={false} actions={<Link to="/app/records?mine=1" className="text-sm text-secondary">Xem tất cả</Link>}>
          {mine.isPending ? <ListSkeleton rows={4} withAvatar={false} /> : mine.error ? <div className="p-4"><ErrorState message={errorMessage(mine.error)} /></div>
            : mine.data.items.length === 0 ? <EmptyState icon="folder_open" title="Bạn chưa có hồ sơ nào" action={<Link to="/app/records/new" className="text-secondary text-sm font-medium">Lập hồ sơ đầu tiên</Link>} />
            : (
              <ul className="divide-y divide-outline-variant/20">
                {mine.data.items.map((r) => (
                  <li key={r.id}>
                    <Link to={`/app/records/${r.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-surface-container-low">
                      <span className="flex-1 min-w-0">
                        <span className="block text-sm font-medium">{r.recordCode}</span>
                        <span className="block text-xs text-on-surface-variant">{r.formCode} · tạo {fmt.relative(r.createdAt)}</span>
                      </span>
                      <StatusBadge status={r.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
        </Panel>

        <Panel title="Thông báo" padded={false} className="xl:col-span-2" actions={<Link to="/app/notifications" className="text-sm text-secondary">Xem tất cả</Link>}>
          {notes.isPending ? <ListSkeleton rows={3} /> : notes.error ? <div className="p-4"><ErrorState message={errorMessage(notes.error)} /></div>
            : notes.data.items.length === 0 ? <EmptyState icon="notifications_off" title="Chưa có thông báo" />
            : (
              <ul className="divide-y divide-outline-variant/20">
                {notes.data.items.map((n) => (
                  <li key={n.id} className="flex items-center gap-3 px-5 py-3">
                    <span className={`w-2 h-2 rounded-full ${n.read ? 'bg-transparent' : 'bg-secondary'}`} aria-label={n.read ? undefined : 'Chưa đọc'} />
                    <span className="flex-1 text-sm">{n.title}</span>
                    <span className="text-xs text-on-surface-variant">{fmt.relative(n.createdAt)}</span>
                  </li>
                ))}
              </ul>
            )}
        </Panel>
      </div>
    </>
  );
}
