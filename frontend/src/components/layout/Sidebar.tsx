import { NavLink } from 'react-router';
import { Icon } from '../ui/Primitives';
import { usePermissions } from '../../api/identity';
import { useInbox } from '../../api/workflow';
import type { Action } from '../../api/types';

interface Item {
  to: string;
  label: string;
  icon: string;
  /** resource + action cần có để hiện mục (ẩn mục chỉ là UX; backend vẫn kiểm tra quyền) */
  need?: [string, Action];
  soon?: boolean;
  badge?: 'tasks';
}

const groups: { title: string; items: Item[] }[] = [
  {
    title: 'Công việc',
    items: [
      { to: '/app', label: 'Tổng quan', icon: 'space_dashboard' },
      { to: '/app/tasks', label: 'Hộp việc & Phê duyệt', icon: 'task_alt', badge: 'tasks' },
      { to: '/app/records', label: 'Hồ sơ', icon: 'folder_open' },
      { to: '/app/notifications', label: 'Thông báo', icon: 'notifications' },
    ],
  },
  {
    title: 'Cấu hình',
    items: [
      { to: '/app/forms', label: 'Biểu mẫu', icon: 'view_quilt', need: ['metadata', 'VIEW'] },
      { to: '/app/workflows', label: 'Quy trình', icon: 'account_tree', need: ['workflow', 'VIEW'] },
    ],
  },
  {
    title: 'Quản trị đơn vị',
    items: [
      { to: '/app/members', label: 'Thành viên', icon: 'group', need: ['identity', 'VIEW'] },
      { to: '/app/roles', label: 'Vai trò & Phân quyền', icon: 'admin_panel_settings', need: ['identity', 'VIEW'] },
      { to: '/app/audit', label: 'Nhật ký kiểm toán', icon: 'policy', need: ['audit', 'AUDIT'] },
      { to: '/app/batch-jobs', label: 'Giám sát Batch Job', icon: 'dns', soon: true },
      { to: '/app/auth-sources', label: 'Nguồn xác thực & SSO', icon: 'key', soon: true },
    ],
  },
];

const RESOURCES = ['metadata', 'workflow', 'identity', 'audit'];

function TaskBadge() {
  const { data } = useInbox(0, 1);
  return data && data.total > 0 ? (
    <span className="px-2 py-0.5 rounded-full bg-secondary-container text-white text-[11px] font-bold">{data.total}</span>
  ) : null;
}

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center justify-between px-2.5 py-2 rounded-lg text-sm transition-colors ${
    isActive ? 'bg-primary-container text-white font-semibold shadow-sm'
      : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'}`;

export function Sidebar({ platformAdmin, hasTenant }: { platformAdmin: boolean; hasTenant: boolean }) {
  const { can } = usePermissions(hasTenant ? RESOURCES : []);
  return (
    <aside className="fixed left-0 top-16 bottom-0 w-64 z-40 bg-surface-container-lowest border-r border-outline-variant/40 overflow-y-auto">
      <nav aria-label="Điều hướng chính" className="p-4 space-y-6">
        {hasTenant ? groups.map((g) => {
          const visible = g.items.filter((i) => !i.need || can(i.need[0], i.need[1]));
          return visible.length ? (
            <div key={g.title}>
              <p className="px-2 mb-2 text-[11px] uppercase tracking-wider font-semibold text-outline">{g.title}</p>
              <ul className="space-y-0.5">
                {visible.map((i) => (
                  <li key={i.to}>
                    <NavLink to={i.to} end={i.to === '/app'} className={linkClass}>
                      <span className="flex items-center gap-2.5"><Icon name={i.icon} />{i.label}</span>
                      {i.badge === 'tasks' ? <TaskBadge /> : null}
                      {i.soon ? <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-container text-outline font-semibold">Sắp có</span> : null}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ) : null;
        }) : null}
        {platformAdmin ? (
          <div>
            <p className="px-2 mb-2 text-[11px] uppercase tracking-wider font-semibold text-outline">Nền tảng</p>
            <NavLink to="/app/platform/tenants" className={linkClass}>
              <span className="flex items-center gap-2.5"><Icon name="domain" />Đơn vị (tenant)</span>
            </NavLink>
          </div>
        ) : null}
      </nav>
    </aside>
  );
}
