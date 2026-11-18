import { useState } from 'react';
import { Link } from 'react-router';
import { notificationsApi, useNotificationMutation, useNotifications } from '../api/misc';
import { Button } from '../components/ui/Button';
import { EmptyState, ErrorState, Icon, PageHeader, Pager, Panel } from '../components/ui/Primitives';
import { ListSkeleton } from '../components/ui/Skeleton';
import { fmt } from '../lib/format';
import { errorMessage } from '../lib/http';

const SIZE = 20;
const iconOf = (type: string) =>
  type.includes('APPROVED') ? 'verified' : type.includes('REJECTED') ? 'block' : type.includes('RETURNED') ? 'undo' : type.includes('TASK') ? 'assignment' : 'notifications';

export default function Notifications() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const q = useNotifications(unreadOnly, page, SIZE);
  const read = useNotificationMutation(notificationsApi.read);
  const readAll = useNotificationMutation(notificationsApi.readAll);

  return (
    <>
      <PageHeader title="Thông báo" subtitle="Kết quả xử lý hồ sơ và công việc mới được giao."
        actions={<Button variant="secondary" disabled={readAll.isPending} onClick={() => readAll.mutate(undefined)}>
          <Icon name="done_all" className="text-[18px] mr-1.5" />Đánh dấu đã đọc tất cả</Button>} />
      <div className="flex gap-2 mb-4" role="tablist">
        {[false, true].map((u) => (
          <button key={String(u)} type="button" role="tab" aria-selected={unreadOnly === u}
            onClick={() => { setUnreadOnly(u); setPage(0); }}
            className={`h-9 px-4 rounded-full text-sm ${unreadOnly === u ? 'bg-primary text-white' : 'bg-surface-container-low text-on-surface-variant'}`}>
            {u ? 'Chưa đọc' : 'Tất cả'}
          </button>
        ))}
      </div>
      <Panel padded={false}>
        {q.isPending ? <div className="p-5"><ListSkeleton rows={6} /></div>
          : q.error ? <div className="p-5"><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></div>
          : q.data.items.length === 0 ? <EmptyState icon="notifications_off" title="Không có thông báo" />
          : (
            <ul className="divide-y divide-outline-variant/20">
              {q.data.items.map((n) => (
                <li key={n.id} className={`flex gap-4 px-5 py-4 ${n.read ? '' : 'bg-secondary-fixed/20'}`}>
                  <span className="w-9 h-9 shrink-0 rounded-full bg-surface-container flex items-center justify-center text-primary"><Icon name={iconOf(n.type)} /></span>
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm ${n.read ? '' : 'font-semibold'}`}>{n.title}</p>
                    {n.body ? <p className="text-sm text-on-surface-variant mt-0.5">{n.body}</p> : null}
                    <p className="text-xs text-outline mt-1">{fmt.relative(n.createdAt)}</p>
                  </div>
                  <div className="flex items-start gap-2">
                    {n.refType === 'record' && n.refId ? <Link to={`/app/records/${n.refId}`} className="text-sm text-secondary">Xem hồ sơ</Link> : null}
                    {n.refType === 'task' ? <Link to="/app/tasks" className="text-sm text-secondary">Mở hộp việc</Link> : null}
                    {!n.read ? <Button size="sm" variant="ghost" onClick={() => read.mutate(n.id)}>Đã đọc</Button> : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        {q.data && q.data.total > SIZE ? <div className="p-4"><Pager page={page} size={SIZE} total={q.data.total} onPage={setPage} /></div> : null}
      </Panel>
    </>
  );
}
