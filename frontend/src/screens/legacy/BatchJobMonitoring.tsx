import { useState, useEffect } from 'react';
import { PageSkeleton } from '../../components/ui/PageSkeleton';

export const BatchJobMonitoring = () => {
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [isToastVisible, setIsToastVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1200);
    return () => clearTimeout(timer);
  }, []);

  const toggleDrawer = (show: boolean) => {
    setIsDrawerOpen(show);
  };

  const openConfirmModal = () => {
    setIsConfirmModalOpen(true);
  };

  const closeConfirmModal = () => {
    setIsConfirmModalOpen(false);
  };

  const executeReplay = () => {
    closeConfirmModal();
    toggleDrawer(false);
    
    setIsToastVisible(true);
    
    setTimeout(() => {
      setIsToastVisible(false);
    }, 3500);
  };

  if (isLoading) {
    return <PageSkeleton />;
  }

  return (
    <main className="w-full bg-surface min-h-screen px-margin-sm md:px-margin-lg py-margin">
      <div className="flex flex-col w-full pb-16 relative">
        {/* Page Header & Global Controls */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="px-2 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed font-label-xs text-[11px] font-semibold">Hạ tầng phân tán</span>
              <span className="text-outline text-xs">•</span>
              <span className="text-on-surface-variant font-label-xs text-xs">Cập nhật thời gian thực: 2s trước</span>
            </div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight font-bold">Giám sát Batch Job &amp; Hàng đợi</h1>
          </div>
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-2 h-10 px-4 rounded-lg bg-surface-container-lowest text-on-surface font-label-md text-label-md shadow-sm hover:bg-surface-container-low transition-colors border border-outline-variant/40" type="button">
              <span className="material-symbols-outlined text-[18px] text-outline">refresh</span>
              <span>Làm mới dữ liệu</span>
            </button>
            <button className="flex items-center gap-2 h-10 px-4 rounded-lg bg-primary text-on-primary font-label-md text-label-md shadow-sm hover:bg-primary-container transition-colors" type="button">
              <span className="material-symbols-outlined text-[18px]">add_task</span>
              <span>Tạo Batch Job mới</span>
            </button>
          </div>
        </div>

        {/* Metrics Summary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {/* Card 1 */}
          <div className="p-5 rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(15,23,42,0.05),0_1px_2px_-1px_rgba(15,23,42,0.05)] border border-outline-variant/30 relative overflow-hidden group">
            <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-primary/5 rounded-full group-hover:scale-110 transition-transform"></div>
            <div className="flex items-center justify-between mb-3">
              <span className="font-label-sm text-on-surface-variant">Batch đang chạy</span>
              <span className="w-8 h-8 rounded-lg bg-primary-container/10 text-primary flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">play_arrow</span>
              </span>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-3xl font-bold text-on-surface">14</span>
              <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded">Ổn định</span>
            </div>
            <p className="text-xs text-outline">Trên tổng số 18 worker nodes</p>
          </div>
          {/* Card 2 */}
          <div className="p-5 rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(15,23,42,0.05),0_1px_2px_-1px_rgba(15,23,42,0.05)] border border-outline-variant/30 relative overflow-hidden group">
            <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-emerald-500/5 rounded-full group-hover:scale-110 transition-transform"></div>
            <div className="flex items-center justify-between mb-3">
              <span className="font-label-sm text-on-surface-variant">Tỷ lệ thành công 24h</span>
              <span className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-600 flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">verified</span>
              </span>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-3xl font-bold text-on-surface">98.4%</span>
              <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded">+0.2%</span>
            </div>
            <p className="text-xs text-outline">1,240 / 1,260 job hoàn thành</p>
          </div>
          {/* Card 3 */}
          <div className="p-5 rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(15,23,42,0.05),0_1px_2px_-1px_rgba(15,23,42,0.05)] border border-outline-variant/30 relative overflow-hidden group">
            <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-secondary/5 rounded-full group-hover:scale-110 transition-transform"></div>
            <div className="flex items-center justify-between mb-3">
              <span className="font-label-sm text-on-surface-variant">Thông lượng trung bình</span>
              <span className="w-8 h-8 rounded-lg bg-secondary/10 text-secondary flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">speed</span>
              </span>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-3xl font-bold text-on-surface">14,250</span>
              <span className="text-xs font-semibold text-on-surface-variant">bản ghi/s</span>
            </div>
            <p className="text-xs text-outline">Độ trễ trung bình 45ms</p>
          </div>
          {/* Card 4 */}
          <div className="p-5 rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(15,23,42,0.05),0_1px_2px_-1px_rgba(15,23,42,0.05)] border border-outline-variant/30 relative overflow-hidden group ring-2 ring-error/20">
            <div className="absolute -right-4 -bottom-4 w-24 h-24 bg-error/5 rounded-full group-hover:scale-110 transition-transform"></div>
            <div className="flex items-center justify-between mb-3">
              <span className="font-label-sm text-on-surface-variant">Hàng đợi DLQ (Lỗi)</span>
              <span className="w-8 h-8 rounded-lg bg-error/10 text-error flex items-center justify-center">
                <span className="material-symbols-outlined text-[20px]">error</span>
              </span>
            </div>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="text-3xl font-bold text-error">3</span>
              <span className="text-xs font-semibold text-error bg-error-container/50 px-1.5 py-0.5 rounded">Cần xử lý</span>
            </div>
            <p className="text-xs text-outline">Dead Letter Queue active</p>
          </div>
        </div>

        {/* Filters & Search Bar */}
        <div className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-4 p-4 rounded-xl bg-surface-container-lowest shadow-sm border border-outline-variant/30 mb-6">
          {/* Status Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-2 lg:pb-0">
            <button className="px-3.5 py-1.5 rounded-lg bg-primary text-on-primary font-label-md text-xs font-semibold transition-colors whitespace-nowrap" type="button">Tất cả (18)</button>
            <button className="px-3.5 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface-variant font-label-md text-xs font-semibold transition-colors whitespace-nowrap" type="button">Đang chạy (14)</button>
            <button className="px-3.5 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface-variant font-label-md text-xs font-semibold transition-colors whitespace-nowrap flex items-center gap-1.5" type="button">
              <span>Thất bại</span>
              <span className="px-1.5 py-0.5 rounded-full bg-error text-on-error text-[10px] font-bold">3</span>
            </button>
            <button className="px-3.5 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface-variant font-label-md text-xs font-semibold transition-colors whitespace-nowrap" type="button">Đã hoàn thành (1)</button>
          </div>
          {/* Search & Filters */}
          <div className="flex items-center gap-3">
            <div className="relative flex-1 lg:w-72">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
              <input className="w-full h-9 pl-9 pr-3 bg-surface-container-low text-on-surface font-body-sm text-xs rounded-lg border border-outline-variant/40 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none transition-all placeholder:text-outline" placeholder="Tìm kiếm theo Mã Job, Tên tác vụ..." type="text"/>
            </div>
            <button className="flex items-center gap-1.5 h-9 px-3 rounded-lg bg-surface-container-low text-on-surface-variant hover:text-on-surface font-label-md text-xs border border-outline-variant/40 transition-colors" type="button">
              <span className="material-symbols-outlined text-[16px]">filter_list</span>
              <span>Bộ lọc khác</span>
            </button>
          </div>
        </div>

        {/* Main Jobs Table */}
        <div className="rounded-xl bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(15,23,42,0.05),0_1px_2px_-1px_rgba(15,23,42,0.05)] border border-outline-variant/30 overflow-hidden mb-8">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant/30">
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Mã Job (ID)</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Tên tiến trình / Tác vụ</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Lịch chạy</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Trạng thái</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold w-40">Tiến độ</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Thông lượng</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold">Lỗi</th>
                  <th className="py-3 px-4 font-label-xs text-outline text-[11px] uppercase tracking-wider font-semibold text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/20 font-body-sm text-xs">
                {/* Row 1: Failed Job (Target for drawer demo) */}
                <tr className="hover:bg-surface-container-low/50 transition-colors bg-error/5 cursor-pointer" onClick={() => toggleDrawer(true)}>
                  <td className="py-3 px-4 font-mono font-semibold text-primary">JOB-2024-9912</td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-on-surface">Sync_Enterprise_ERP_MasterData</div>
                    <div className="text-[11px] text-outline">Đồng bộ danh mục nhân sự &amp; chi nhánh cấp 1</div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant">Mỗi 30 phút</td>
                  <td className="py-3 px-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-error-container text-on-error-container border border-error/20">
                      Thất bại (DLQ)
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
                        <div className="h-full bg-error rounded-full" style={{ width: '78%' }}></div>
                      </div>
                      <span className="text-[11px] font-medium text-error">78%</span>
                    </div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant font-mono">0 rec/s</td>
                  <td className="py-3 px-4">
                    <span className="px-2 py-0.5 rounded bg-error/10 text-error font-semibold text-[11px]">3 lỗi</span>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-1.5" onClick={(e) => e.stopPropagation()}>
                      <button className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-primary transition-colors" title="Thử lại Job" type="button">
                        <span className="material-symbols-outlined text-[18px]">replay</span>
                      </button>
                      <button className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-primary hover:text-on-primary text-on-surface transition-colors font-medium" onClick={() => toggleDrawer(true)} type="button">
                        <span>Chi tiết</span>
                        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                      </button>
                    </div>
                  </td>
                </tr>
                {/* Row 2: Running Job */}
                <tr className="hover:bg-surface-container-low/50 transition-colors">
                  <td className="py-3 px-4 font-mono font-semibold text-primary">JOB-2024-9913</td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-on-surface">Process_Invoice_Batch_Q3</div>
                    <div className="text-[11px] text-outline">Xử lý hóa đơn điện tử hàng loạt</div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant">Thời gian thực</td>
                  <td className="py-3 px-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-secondary-fixed text-on-secondary-fixed border border-secondary/20">
                      Đang chạy
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
                        <div className="h-full bg-secondary rounded-full animate-pulse" style={{ width: '45%' }}></div>
                      </div>
                      <span className="text-[11px] font-medium text-secondary">45%</span>
                    </div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant font-mono">18,400 rec/s</td>
                  <td className="py-3 px-4 text-outline">0</td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <button className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-error transition-colors" title="Tạm dừng" type="button">
                        <span className="material-symbols-outlined text-[18px]">pause</span>
                      </button>
                      <button className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-primary hover:text-on-primary text-on-surface transition-colors font-medium" type="button">
                        <span>Chi tiết</span>
                        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                      </button>
                    </div>
                  </td>
                </tr>
                {/* Row 3: Running Job */}
                <tr className="hover:bg-surface-container-low/50 transition-colors">
                  <td className="py-3 px-4 font-mono font-semibold text-primary">JOB-2024-9914</td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-on-surface">Telemetry_Log_Aggregation</div>
                    <div className="text-[11px] text-outline">Tổng hợp log hệ thống phân tán</div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant">Mỗi 15 phút</td>
                  <td className="py-3 px-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-secondary-fixed text-on-secondary-fixed border border-secondary/20">
                      Đang chạy
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
                        <div className="h-full bg-secondary rounded-full" style={{ width: '92%' }}></div>
                      </div>
                      <span className="text-[11px] font-medium text-secondary">92%</span>
                    </div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant font-mono">22,150 rec/s</td>
                  <td className="py-3 px-4 text-outline">0</td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <button className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-error transition-colors" title="Tạm dừng" type="button">
                        <span className="material-symbols-outlined text-[18px]">pause</span>
                      </button>
                      <button className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-primary hover:text-on-primary text-on-surface transition-colors font-medium" type="button">
                        <span>Chi tiết</span>
                        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                      </button>
                    </div>
                  </td>
                </tr>
                {/* Row 4: Completed Job */}
                <tr className="hover:bg-surface-container-low/50 transition-colors">
                  <td className="py-3 px-4 font-mono font-semibold text-primary">JOB-2024-9910</td>
                  <td className="py-3 px-4">
                    <div className="font-semibold text-on-surface">Nightly_DB_Backup_Snapshot</div>
                    <div className="text-[11px] text-outline">Sao lưu toàn bộ cơ sở dữ liệu chính</div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant">Hằng ngày (02:00)</td>
                  <td className="py-3 px-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                      Hoàn thành
                    </span>
                  </td>
                  <td className="py-3 px-4">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-2 rounded-full bg-surface-container overflow-hidden">
                        <div className="h-full bg-emerald-500 rounded-full" style={{ width: '100%' }}></div>
                      </div>
                      <span className="text-[11px] font-medium text-emerald-600">100%</span>
                    </div>
                  </td>
                  <td className="py-3 px-4 text-on-surface-variant font-mono">11,200 rec/s</td>
                  <td className="py-3 px-4 text-outline">0</td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <button className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-primary transition-colors" title="Chạy lại" type="button">
                        <span className="material-symbols-outlined text-[18px]">replay</span>
                      </button>
                      <button className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-surface-container hover:bg-primary hover:text-on-primary text-on-surface transition-colors font-medium" type="button">
                        <span>Chi tiết</span>
                        <span className="material-symbols-outlined text-[14px]">chevron_right</span>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          {/* Pagination */}
          <div className="flex items-center justify-between px-4 py-3 bg-surface-container-low border-t border-outline-variant/30 text-xs">
            <span className="text-on-surface-variant">Hiển thị 1-4 trên tổng số 18 bản ghi Batch</span>
            <div className="flex items-center gap-1">
              <button className="px-3 py-1 rounded bg-surface-container-lowest text-outline border border-outline-variant/40 disabled:opacity-50" disabled type="button">Trước</button>
              <button className="px-3 py-1 rounded bg-primary text-on-primary font-semibold" type="button">1</button>
              <button className="px-3 py-1 rounded bg-surface-container-lowest text-on-surface hover:bg-surface-container border border-outline-variant/40" type="button">2</button>
              <button className="px-3 py-1 rounded bg-surface-container-lowest text-on-surface hover:bg-surface-container border border-outline-variant/40" type="button">3</button>
              <button className="px-3 py-1 rounded bg-surface-container-lowest text-on-surface hover:bg-surface-container border border-outline-variant/40" type="button">Sau</button>
            </div>
          </div>
        </div>

        {/* Detail Drawer (JOB-2024-9912) */}
        <div className={`fixed inset-0 z-50 overflow-hidden transition-all duration-300 ${isDrawerOpen ? 'visible opacity-100' : 'invisible opacity-0'}`} style={{ pointerEvents: isDrawerOpen ? 'auto' : 'none' }}>
          {/* Backdrop */}
          <div className="absolute inset-0 bg-inverse-surface/40 backdrop-blur-sm transition-opacity" onClick={() => toggleDrawer(false)}></div>
          {/* Drawer Panel */}
          <div className={`absolute inset-y-0 right-0 max-w-2xl w-full bg-surface-container-lowest shadow-2xl flex flex-col transform transition-transform duration-300 ${isDrawerOpen ? 'translate-x-0' : 'translate-x-full'}`}>
            {/* Drawer Header */}
            <div className="px-6 py-4 bg-surface-container-low border-b border-outline-variant/30 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="w-10 h-10 rounded-xl bg-error/10 text-error flex items-center justify-center">
                  <span className="material-symbols-outlined text-[22px]">warning</span>
                </span>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-primary">JOB-2024-9912</span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-error-container text-on-error-container">Lỗi DLQ</span>
                  </div>
                  <h2 className="text-base font-bold text-on-surface">Sync_Enterprise_ERP_MasterData</h2>
                </div>
              </div>
              <button className="w-9 h-9 rounded-lg flex items-center justify-center text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => toggleDrawer(false)} type="button">
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>
            {/* Drawer Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Overview & Metadata */}
              <div className="grid grid-cols-2 gap-4 p-4 rounded-xl bg-surface-container-low border border-outline-variant/30">
                <div>
                  <span className="text-outline text-[11px] uppercase tracking-wider font-semibold block mb-1">Worker thực thi</span>
                  <span className="font-mono text-xs text-on-surface font-medium">worker-node-sg-04</span>
                </div>
                <div>
                  <span className="text-outline text-[11px] uppercase tracking-wider font-semibold block mb-1">Thời gian bắt đầu</span>
                  <span className="text-xs text-on-surface font-medium">10:45:20 - 24/10/2024</span>
                </div>
                <div>
                  <span className="text-outline text-[11px] uppercase tracking-wider font-semibold block mb-1">Tổng bản ghi xử lý</span>
                  <span className="text-xs text-on-surface font-medium">14,200 / 18,000</span>
                </div>
                <div>
                  <span className="text-outline text-[11px] uppercase tracking-wider font-semibold block mb-1">Cơ chế phân phối</span>
                  <span className="text-xs text-on-surface font-medium">Kafka Topic / ERP-Sync</span>
                </div>
              </div>
              {/* Timeline */}
              <div>
                <h3 className="font-headline-md text-sm text-on-surface font-semibold mb-4">Mốc thời gian thực hiện (Execution Timeline)</h3>
                <div className="space-y-4 pl-4 border-l-2 border-outline-variant/40 ml-2">
                  <div className="relative pl-6">
                    <span className="absolute -left-[31px] top-0 w-4 h-4 rounded-full bg-emerald-500 border-2 border-surface-container-lowest"></span>
                    <div className="text-xs font-semibold text-on-surface">1. Khởi tạo kết nối &amp; Đọc cấu hình</div>
                    <div className="text-[11px] text-outline">Hoàn thành lúc 10:45:22 (Thời gian: 2.1s)</div>
                  </div>
                  <div className="relative pl-6">
                    <span className="absolute -left-[31px] top-0 w-4 h-4 rounded-full bg-emerald-500 border-2 border-surface-container-lowest"></span>
                    <div className="text-xs font-semibold text-on-surface">2. Đọc dữ liệu từ nguồn Kafka</div>
                    <div className="text-[11px] text-outline">Đã đọc 18,000 bản ghi thành công</div>
                  </div>
                  <div className="relative pl-6">
                    <span className="absolute -left-[31px] top-0 w-4 h-4 rounded-full bg-emerald-500 border-2 border-surface-container-lowest"></span>
                    <div className="text-xs font-semibold text-on-surface">3. Xử lý batch logic nghiệp vụ</div>
                    <div className="text-[11px] text-outline">Hoàn thành 14,200 bản ghi hợp lệ</div>
                  </div>
                  <div className="relative pl-6">
                    <span className="absolute -left-[31px] top-0 w-4 h-4 rounded-full bg-error border-2 border-surface-container-lowest animate-pulse"></span>
                    <div className="text-xs font-semibold text-error">4. Lỗi ghi cơ sở dữ liệu đích (DB Write Error)</div>
                    <div className="text-[11px] text-error/80">Tiến trình bị dừng đột ngột do 3 bản ghi vi phạm ràng buộc khóa ngoại</div>
                  </div>
                </div>
              </div>
              {/* Failed-record list */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-headline-md text-sm text-on-surface font-semibold">Danh sách bản ghi lỗi (Dead Letter Queue - 3 items)</h3>
                  <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface text-xs font-medium transition-colors border border-outline-variant/30" type="button">
                    <span className="material-symbols-outlined text-[16px]">download</span>
                    <span>Tải xuống tệp lỗi (CSV)</span>
                  </button>
                </div>
                <div className="space-y-2.5">
                  {/* Error Item 1 */}
                  <div className="p-3 rounded-xl bg-error/5 border border-error/20">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-mono text-xs font-bold text-primary">ID: EMP-8821</span>
                      <span className="text-[10px] font-semibold text-error bg-error-container px-2 py-0.5 rounded">ConstraintViolation</span>
                    </div>
                    <p className="text-xs text-on-surface font-mono bg-surface-container-lowest p-2 rounded border border-outline-variant/20">
                      ConstraintViolation: Foreign key not found for ID: EMP-8821 in table [dim_departments]
                    </p>
                  </div>
                  {/* Error Item 2 */}
                  <div className="p-3 rounded-xl bg-error/5 border border-error/20">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-mono text-xs font-bold text-primary">ID: EMP-8822</span>
                      <span className="text-[10px] font-semibold text-error bg-error-container px-2 py-0.5 rounded">TimeoutException</span>
                    </div>
                    <p className="text-xs text-on-surface font-mono bg-surface-container-lowest p-2 rounded border border-outline-variant/20">
                      TimeoutException: Kafka broker ack timeout after 30000ms on partition 2
                    </p>
                  </div>
                  {/* Error Item 3 */}
                  <div className="p-3 rounded-xl bg-error/5 border border-error/20">
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-mono text-xs font-bold text-primary">ID: EMP-8823</span>
                      <span className="text-[10px] font-semibold text-error bg-error-container px-2 py-0.5 rounded">ConstraintViolation</span>
                    </div>
                    <p className="text-xs text-on-surface font-mono bg-surface-container-lowest p-2 rounded border border-outline-variant/20">
                      ConstraintViolation: Foreign key not found for ID: EMP-8823 in table [dim_departments]
                    </p>
                  </div>
                </div>
              </div>
            </div>
            {/* Drawer Footer Action */}
            <div className="p-4 bg-surface-container-low border-t border-outline-variant/30 flex items-center justify-between">
              <button className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-xs transition-colors" onClick={() => toggleDrawer(false)} type="button">
                Đóng
              </button>
              <button className="flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-on-primary font-label-md text-xs shadow-sm hover:bg-primary-container transition-colors" onClick={openConfirmModal} type="button">
                <span className="material-symbols-outlined text-[16px]">replay</span>
                <span>Phát lại hàng đợi DLQ (Replay Action)</span>
              </button>
            </div>
          </div>
        </div>

        {/* Safe Confirmation Modal for DLQ Replay */}
        <div className={`fixed inset-0 z-50 flex items-center justify-center transition-all duration-300 ${isConfirmModalOpen ? 'visible opacity-100' : 'invisible opacity-0'}`} style={{ pointerEvents: isConfirmModalOpen ? 'auto' : 'none' }}>
          <div className="absolute inset-0 bg-inverse-surface/40 backdrop-blur-sm" onClick={closeConfirmModal}></div>
          <div className={`relative w-full max-w-md bg-surface-container-lowest rounded-2xl shadow-2xl p-6 border border-outline-variant/30 z-10 m-4 transform transition-all duration-300 ${isConfirmModalOpen ? 'scale-100' : 'scale-95'}`}>
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined text-[24px]">verified_user</span>
            </div>
            <h3 className="text-base font-bold text-on-surface mb-2">Xác thực phát lại hàng đợi DLQ</h3>
            <p className="text-xs text-on-surface-variant mb-6">
              Bạn chuẩn bị phát lại 3 bản ghi lỗi trong hàng đợi DLQ của tiến trình <strong className="text-on-surface">JOB-2024-9912</strong>. Hành động này sẽ kích hoạt worker xử lý lại các bản ghi đã bỏ lỡ.
            </p>
            <div className="flex items-center justify-end gap-3">
              <button className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-xs transition-colors" onClick={closeConfirmModal} type="button">
                Hủy bỏ
              </button>
              <button className="px-4 py-2 rounded-lg bg-primary text-on-primary font-label-md text-xs shadow-sm hover:bg-primary-container transition-colors" onClick={executeReplay} type="button">
                Xác nhận phát lại
              </button>
            </div>
          </div>
        </div>

        {/* Toast Notification Simulator */}
        <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-3 px-4 py-3 rounded-xl bg-surface-container-lowest shadow-xl border border-outline-variant/30 transform transition-all duration-300 ${isToastVisible ? 'translate-y-0 opacity-100' : 'translate-y-24 opacity-0'}`} style={{ pointerEvents: isToastVisible ? 'auto' : 'none' }}>
          <span className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-600 flex items-center justify-center">
            <span className="material-symbols-outlined text-[20px]">check_circle</span>
          </span>
          <div>
            <div className="text-xs font-bold text-on-surface">Thành công</div>
            <div className="text-[11px] text-on-surface-variant">Đã gửi yêu cầu phát lại DLQ thành công!</div>
          </div>
        </div>
      </div>
    </main>
  );
};
