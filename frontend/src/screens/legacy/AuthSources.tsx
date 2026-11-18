import { useState, useEffect } from 'react';
import { PageSkeleton } from '../../components/ui/PageSkeleton';

export const AuthSources = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(1);
  const [modalTitle, setModalTitle] = useState('Thêm nguồn xác thực mới');
  const [sourceName, setSourceName] = useState('Active Directory - Da Nang Branch');
  const [isLoading, setIsLoading] = useState(true);
  
  const [toast, setToast] = useState({ visible: false, title: '', desc: '' });

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1200);
    return () => clearTimeout(timer);
  }, []);

  const showToast = (title: string, desc: string) => {
    setToast({ visible: true, title, desc });
    setTimeout(() => {
      setToast(prev => ({ ...prev, visible: false }));
    }, 3500);
  };

  const openAddModal = () => {
    setCurrentStep(1);
    setModalTitle('Thêm nguồn xác thực mới');
    setSourceName('Active Directory - Da Nang Branch');
    setIsModalOpen(true);
  };

  const openEditModal = (name: string) => {
    setCurrentStep(1);
    setModalTitle('Chỉnh sửa nguồn xác thực: ' + name);
    setSourceName(name);
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
  };

  const nextStep = () => {
    if (currentStep < 3) {
      setCurrentStep(currentStep + 1);
    } else {
      closeModal();
      showToast('Lưu cấu hình thành công', 'Nguồn xác thực đã được cập nhật vào hệ thống.');
    }
  };

  const prevStep = () => {
    if (currentStep > 1) {
      setCurrentStep(currentStep - 1);
    }
  };

  const testConnectionToast = (name: string) => {
    showToast('Kết nối thành công', 'Kết nối tới ' + name + ' phản hồi ổn định (12ms).');
  };

  const simulateTest = () => {
    showToast('Đang kiểm tra...', 'Đã gửi gói tin ping tới máy chủ thư mục.');
    setTimeout(() => {
      showToast('Kiểm tra thành công', 'Phản hồi 10ms - Đạt tiêu chuẩn hệ thống.');
    }, 1000);
  };

  if (isLoading) {
    return <PageSkeleton />;
  }

  return (
    <main className="w-full bg-surface min-h-screen px-margin-sm md:px-margin-lg py-margin">
      <div className="flex flex-col w-full">
        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-space-md mb-margin">
          <div>
            <div className="flex items-center gap-space-xs mb-1">
              <span className="font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Thiết lập hệ thống</span>
              <span className="text-outline text-[12px]">/</span>
              <span className="font-label-xs text-label-xs text-primary font-semibold uppercase tracking-wider">Xác thực &amp; SSO</span>
            </div>
            <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight font-bold">Quản lý nguồn xác thực &amp; Thư mục (Auth Sources)</h1>
          </div>
          <div className="flex items-center gap-space-sm">
            <button className="flex items-center gap-space-sm h-10 px-space-md rounded-lg bg-surface-container-low hover:bg-surface-container text-on-surface font-label-md text-label-md transition-colors shadow-sm" type="button">
              <span className="material-symbols-outlined text-[18px]">sync</span>
              <span>Đồng bộ toàn bộ</span>
            </button>
            <button className="flex items-center gap-space-sm h-10 px-space-md rounded-lg bg-primary text-on-primary font-label-md text-label-md hover:bg-primary-container transition-colors shadow-sm" onClick={openAddModal} type="button">
              <span className="material-symbols-outlined text-[18px]">add</span>
              <span>Thêm nguồn xác thực mới</span>
            </button>
          </div>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-space-md mb-margin">
          {/* Card 1 */}
          <div className="p-space-md rounded-xl bg-surface-container-lowest shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between mb-space-sm">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase">Tổng số nguồn</span>
              <div className="w-8 h-8 rounded-lg bg-primary-fixed flex items-center justify-center text-primary">
                <span className="material-symbols-outlined text-[18px]">hub</span>
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">04</span>
              <span className="font-label-xs text-label-xs text-secondary font-semibold">Đã kích hoạt</span>
            </div>
            <p className="mt-1 font-body-sm text-outline">Toàn hệ thống tập đoàn</p>
          </div>
          {/* Card 2 */}
          <div className="p-space-md rounded-xl bg-surface-container-lowest shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between mb-space-sm">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase">Trạng thái hoạt động</span>
              <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-secondary">
                <span className="material-symbols-outlined text-[18px]">check_circle</span>
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">3 / 1</span>
              <span className="font-label-xs text-label-xs text-on-surface-variant font-semibold">Hoạt động / Cảnh báo</span>
            </div>
            <p className="mt-1 font-body-sm text-outline">1 kết nối đồng bộ chậm</p>
          </div>
          {/* Card 3 */}
          <div className="p-space-md rounded-xl bg-surface-container-lowest shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between mb-space-sm">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase">Giao thức hỗ trợ</span>
              <div className="w-8 h-8 rounded-lg bg-tertiary-fixed flex items-center justify-center text-tertiary">
                <span className="material-symbols-outlined text-[18px]">security</span>
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">4</span>
              <span className="font-label-xs text-label-xs text-tertiary font-semibold">Chuẩn quốc tế</span>
            </div>
            <p className="mt-1 font-body-sm text-outline">LDAP, OIDC, SAML, Internal</p>
          </div>
          {/* Card 4 */}
          <div className="p-space-md rounded-xl bg-surface-container-lowest shadow-sm hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between mb-space-sm">
              <span className="font-label-sm text-label-sm text-outline font-semibold uppercase">Tần suất đồng bộ</span>
              <div className="w-8 h-8 rounded-lg bg-surface-container flex items-center justify-center text-primary">
                <span className="material-symbols-outlined text-[18px]">update</span>
              </div>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-display-lg text-display-lg font-bold text-on-surface">15p</span>
              <span className="font-label-xs text-label-xs text-primary font-semibold">Tự động</span>
            </div>
            <p className="mt-1 font-body-sm text-outline">Đồng bộ User &amp; Groups</p>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-space-md mb-margin-sm bg-surface-container-lowest p-space-sm rounded-xl shadow-sm">
          <div className="w-full sm:w-80 relative flex items-center">
            <span className="material-symbols-outlined absolute left-3 text-outline text-[20px]">search</span>
            <input className="w-full h-9 pl-9 pr-4 bg-surface-container-low text-on-surface font-body-sm rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none transition-all placeholder:text-outline" placeholder="Tìm kiếm theo tên nguồn, endpoint..." type="text"/>
          </div>
          <div className="flex items-center gap-space-sm w-full sm:w-auto justify-end">
            <select className="h-9 px-3 bg-surface-container-low text-on-surface font-body-sm rounded-lg border border-outline-variant/50 focus:outline-none">
              <option>Tất cả giao thức</option>
              <option>LDAP / Active Directory</option>
              <option>OIDC / OAuth2</option>
              <option>SAML 2.0</option>
              <option>Internal Database</option>
            </select>
            <select className="h-9 px-3 bg-surface-container-low text-on-surface font-body-sm rounded-lg border border-outline-variant/50 focus:outline-none">
              <option>Tất cả trạng thái</option>
              <option>Đang hoạt động</option>
              <option>Đồng bộ chậm</option>
            </select>
          </div>
        </div>

        {/* Data Table */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm overflow-x-auto max-w-full mb-margin">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-surface-container-low border-b border-outline-variant/40">
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Tên nguồn &amp; Giao thức</th>
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Loại</th>
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Điểm cuối (Endpoint URL / Host)</th>
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Trạng thái kết nối</th>
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider">Lần đồng bộ cuối</th>
                  <th className="py-3 px-space-md font-label-xs text-label-xs text-outline font-semibold uppercase tracking-wider text-right">Tác vụ</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/30">
                <tr className="hover:bg-surface-container-low/60 transition-colors">
                  <td className="py-3 px-space-md">
                    <div className="flex items-center gap-space-sm">
                      <div className="w-9 h-9 rounded-lg bg-primary-fixed/60 flex items-center justify-center text-primary font-bold">AD</div>
                      <div>
                        <div className="font-label-md text-label-md text-on-surface font-semibold">Active Directory - Ho Chi Minh HQ</div>
                        <div className="font-body-sm text-outline">Primary Directory Service</div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-space-md"><span className="px-2 py-0.5 rounded bg-surface-container text-on-surface font-label-xs">LDAP</span></td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant font-mono">ldap://10.0.12.50:389</td>
                  <td className="py-3 px-space-md">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#ECFDF5] text-[#065F46] border border-[#A7F3D0]">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#059669]"></span>Đang hoạt động
                    </span>
                  </td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant">2 phút trước (1,420 user)</td>
                  <td className="py-3 px-space-md text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => testConnectionToast('Active Directory - Ho Chi Minh HQ')} title="Kiểm tra kết nối" type="button"><span className="material-symbols-outlined text-[18px]">bolt</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => openEditModal('Active Directory - Ho Chi Minh HQ')} title="Chỉnh sửa" type="button"><span className="material-symbols-outlined text-[18px]">edit</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-error-container hover:text-error transition-colors" title="Xóa" type="button"><span className="material-symbols-outlined text-[18px]">delete</span></button>
                    </div>
                  </td>
                </tr>
                <tr className="hover:bg-surface-container-low/60 transition-colors">
                  <td className="py-3 px-space-md">
                    <div className="flex items-center gap-space-sm">
                      <div className="w-9 h-9 rounded-lg bg-secondary-fixed flex items-center justify-center text-secondary font-bold">OK</div>
                      <div>
                        <div className="font-label-md text-label-md text-on-surface font-semibold">Okta OIDC Identity Provider</div>
                        <div className="font-body-sm text-outline">SSO cho nhân sự khối kinh doanh</div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-space-md"><span className="px-2 py-0.5 rounded bg-surface-container text-on-surface font-label-xs">OIDC</span></td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant font-mono">https://vinacorp.okta.com/oauth2/v1</td>
                  <td className="py-3 px-space-md">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#ECFDF5] text-[#065F46] border border-[#A7F3D0]">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#059669]"></span>Đang hoạt động
                    </span>
                  </td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant">5 phút trước (850 user)</td>
                  <td className="py-3 px-space-md text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => testConnectionToast('Okta OIDC')} title="Kiểm tra kết nối" type="button"><span className="material-symbols-outlined text-[18px]">bolt</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => openEditModal('Okta OIDC')} title="Chỉnh sửa" type="button"><span className="material-symbols-outlined text-[18px]">edit</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-error-container hover:text-error transition-colors" title="Xóa" type="button"><span className="material-symbols-outlined text-[18px]">delete</span></button>
                    </div>
                  </td>
                </tr>
                <tr className="hover:bg-surface-container-low/60 transition-colors">
                  <td className="py-3 px-space-md">
                    <div className="flex items-center gap-space-sm">
                      <div className="w-9 h-9 rounded-lg bg-[#FEF3C7] flex items-center justify-center text-[#D97706] font-bold">AZ</div>
                      <div>
                        <div className="font-label-md text-label-md text-on-surface font-semibold">Azure Entra ID - Global Cloud</div>
                        <div className="font-body-sm text-outline">Tích hợp Azure AD Tenant chính</div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-space-md"><span className="px-2 py-0.5 rounded bg-surface-container text-on-surface font-label-xs">SAML</span></td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant font-mono">https://login.microsoftonline.com/vinacorp</td>
                  <td className="py-3 px-space-md">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A]">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#D97706]"></span>Đồng bộ chậm
                    </span>
                  </td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant">42 phút trước (3,100 user)</td>
                  <td className="py-3 px-space-md text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => testConnectionToast('Azure Entra ID')} title="Kiểm tra kết nối" type="button"><span className="material-symbols-outlined text-[18px]">bolt</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => openEditModal('Azure Entra ID')} title="Chỉnh sửa" type="button"><span className="material-symbols-outlined text-[18px]">edit</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-error-container hover:text-error transition-colors" title="Xóa" type="button"><span className="material-symbols-outlined text-[18px]">delete</span></button>
                    </div>
                  </td>
                </tr>
                <tr className="hover:bg-surface-container-low/60 transition-colors">
                  <td className="py-3 px-space-md">
                    <div className="flex items-center gap-space-sm">
                      <div className="w-9 h-9 rounded-lg bg-surface-container-high flex items-center justify-center text-on-surface-variant font-bold">DB</div>
                      <div>
                        <div className="font-label-md text-label-md text-on-surface font-semibold">Local Database - System Admins</div>
                        <div className="font-body-sm text-outline">Tài khoản quản trị viên dự phòng</div>
                      </div>
                    </div>
                  </td>
                  <td className="py-3 px-space-md"><span className="px-2 py-0.5 rounded bg-surface-container text-on-surface font-label-xs">Internal</span></td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant font-mono">jdbc:postgresql://internal-db:5432/dynaflow</td>
                  <td className="py-3 px-space-md">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#ECFDF5] text-[#065F46] border border-[#A7F3D0]">
                      <span className="w-1.5 h-1.5 rounded-full bg-[#059669]"></span>Đang hoạt động
                    </span>
                  </td>
                  <td className="py-3 px-space-md font-body-sm text-on-surface-variant">Thời gian thực (12 user)</td>
                  <td className="py-3 px-space-md text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => testConnectionToast('Local Database')} title="Kiểm tra kết nối" type="button"><span className="material-symbols-outlined text-[18px]">bolt</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={() => openEditModal('Local Database')} title="Chỉnh sửa" type="button"><span className="material-symbols-outlined text-[18px]">edit</span></button>
                      <button className="w-8 h-8 flex items-center justify-center rounded-lg text-outline hover:bg-error-container hover:text-error transition-colors" title="Xóa" type="button"><span className="material-symbols-outlined text-[18px]">delete</span></button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="p-space-md bg-surface-container-low/50 border-t border-outline-variant/30 flex items-center justify-between">
            <span className="font-body-sm text-outline">Hiển thị 4 trong tổng số 4 nguồn xác thực</span>
            <div className="flex items-center gap-space-xs">
              <button className="px-3 py-1 rounded bg-surface-container text-outline font-label-xs cursor-not-allowed" disabled type="button">Trước</button>
              <button className="px-3 py-1 rounded bg-primary text-on-primary font-label-xs" type="button">1</button>
              <button className="px-3 py-1 rounded bg-surface-container text-outline font-label-xs cursor-not-allowed" disabled type="button">Sau</button>
            </div>
          </div>
        </div>

        {/* ADD / EDIT WIZARD MODAL */}
        <div className={`fixed inset-0 z-50 flex items-center justify-center transition-all duration-300 ${isModalOpen ? 'visible opacity-100' : 'invisible opacity-0'}`} style={{ pointerEvents: isModalOpen ? 'auto' : 'none' }}>
          <div className="absolute inset-0 bg-inverse-surface/40 backdrop-blur-sm" onClick={closeModal}></div>
          <div className={`relative w-full max-w-3xl bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant/30 overflow-hidden flex flex-col max-h-[921px] transform transition-all duration-300 ${isModalOpen ? 'scale-100' : 'scale-95'}`}>
            {/* Modal Header */}
            <div className="px-space-lg py-space-md border-b border-outline-variant/30 flex items-center justify-between bg-surface-container-low">
              <div className="flex items-center gap-space-sm">
                <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center">
                  <span className="material-symbols-outlined">add_moderator</span>
                </div>
                <div>
                  <h3 className="font-headline-md text-headline-md text-on-surface font-bold">{modalTitle}</h3>
                  <p className="font-body-sm text-outline">Cấu hình kết nối thư mục người dùng và phương thức SSO doanh nghiệp</p>
                </div>
              </div>
              <button className="w-9 h-9 flex items-center justify-center rounded-lg text-outline hover:bg-surface-container hover:text-on-surface transition-colors" onClick={closeModal} type="button">
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>
            {/* Wizard Steps Indicator */}
            <div className="px-space-lg py-space-sm bg-surface-container border-b border-outline-variant/20 flex items-center gap-4 text-xs font-semibold">
              <div className={currentStep >= 1 ? "flex items-center gap-2 text-primary font-semibold" : "flex items-center gap-2 text-outline"}>
                <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${currentStep >= 1 ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-outline'}`}>1</span>
                <span>Thông tin cơ bản</span>
              </div>
              <span className="text-outline">/</span>
              <div className={currentStep >= 2 ? "flex items-center gap-2 text-primary font-semibold" : "flex items-center gap-2 text-outline"}>
                <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${currentStep >= 2 ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-outline'}`}>2</span>
                <span>Ánh xạ thuộc tính</span>
              </div>
              <span className="text-outline">/</span>
              <div className={currentStep >= 3 ? "flex items-center gap-2 text-primary font-semibold" : "flex items-center gap-2 text-outline"}>
                <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${currentStep >= 3 ? 'bg-primary text-on-primary' : 'bg-surface-container-high text-outline'}`}>3</span>
                <span>Kiểm tra kết nối</span>
              </div>
            </div>
            {/* Modal Body (Steps Container) */}
            <div className="p-space-lg overflow-y-auto flex-1 space-y-space-md">
              {/* Step 1 Content */}
              {currentStep === 1 && (
                <div className="space-y-space-md">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md">
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Tên nguồn xác thực *</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none" value={sourceName} onChange={e => setSourceName(e.target.value)} type="text" />
                    </div>
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Giao thức / Loại kết nối *</label>
                      <select className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none">
                        <option>LDAP / Active Directory</option>
                        <option>OIDC / OAuth2 Provider</option>
                        <option>SAML 2.0 Identity Provider</option>
                        <option>Internal Database (JDBC)</option>
                      </select>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-space-md">
                    <div className="md:col-span-2">
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Endpoint URL / Host *</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none" type="text" defaultValue="ldap://10.0.15.20" />
                    </div>
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Cổng (Port) *</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none" type="text" defaultValue="389" />
                    </div>
                  </div>
                  <div>
                    <label className="block font-label-md text-label-md text-on-surface mb-1">Tài khoản Quản trị Bind DN *</label>
                    <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none" type="text" defaultValue="cn=admin,dc=vinacorp,dc=vn" />
                  </div>
                  <div>
                    <label className="block font-label-md text-label-md text-on-surface mb-1">Mật khẩu Bind DN *</label>
                    <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md rounded-lg border border-outline-variant/50 focus:border-secondary focus:bg-surface-container-lowest focus:outline-none" type="password" defaultValue="••••••••••••••••" />
                  </div>
                </div>
              )}
              {/* Step 2 Content */}
              {currentStep === 2 && (
                <div className="space-y-space-md">
                  <div className="p-3 bg-secondary-fixed/30 rounded-lg text-on-secondary-fixed font-body-sm mb-space-sm">
                    Cấu hình liên kết các thuộc tính (Mapping Attributes) từ thư mục LDAP bên ngoài sang trường dữ liệu chuẩn của hệ thống DynaFlow Enterprise.
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md">
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Tên đăng nhập (username)</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:outline-none" type="text" defaultValue="sAMAccountName" />
                    </div>
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Thư điện tử (email)</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:outline-none" type="text" defaultValue="mail" />
                    </div>
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Họ và tên (full_name)</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:outline-none" type="text" defaultValue="cn" />
                    </div>
                    <div>
                      <label className="block font-label-md text-label-md text-on-surface mb-1">Nhóm quyền (groups)</label>
                      <input className="w-full h-10 px-3 bg-surface-container-low text-on-surface font-body-md font-mono rounded-lg border border-outline-variant/50 focus:outline-none" type="text" defaultValue="memberOf" />
                    </div>
                  </div>
                </div>
              )}
              {/* Step 3 Content */}
              {currentStep === 3 && (
                <div className="space-y-space-md text-center py-6">
                  <div className="w-16 h-16 rounded-full bg-[#ECFDF5] text-[#059669] flex items-center justify-center mx-auto mb-4 border border-[#A7F3D0]">
                    <span className="material-symbols-outlined text-[32px]">check</span>
                  </div>
                  <h4 className="font-headline-md text-headline-md text-on-surface font-bold">Kiểm tra kết nối thành công</h4>
                  <p className="font-body-md text-on-surface-variant max-w-md mx-auto bg-surface-container-low p-3 rounded-xl border border-outline-variant/40 font-mono text-xs">
                    Kết nối thành công tới ldap://10.0.15.20:389 - Phản hồi 12ms (Tìm thấy 450 DN entries).
                  </p>
                  <div className="pt-4 flex justify-center">
                    <button className="px-4 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-sm transition-colors flex items-center gap-2" onClick={simulateTest} type="button">
                      <span className="material-symbols-outlined text-[16px]">refresh</span>
                      Kiểm tra lại kết nối
                    </button>
                  </div>
                </div>
              )}
            </div>
            {/* Modal Footer */}
            <div className="px-space-lg py-space-md border-t border-outline-variant/30 bg-surface-container-low flex items-center justify-between">
              <button 
                className={`px-4 py-2 rounded-lg font-label-md ${currentStep === 1 ? 'bg-surface-container text-outline cursor-not-allowed' : 'bg-surface-container text-on-surface hover:bg-surface-container-high transition-colors'}`} 
                disabled={currentStep === 1} 
                onClick={prevStep} 
                type="button"
              >
                Quay lại
              </button>
              <div className="flex items-center gap-space-sm">
                <button className="px-4 py-2 rounded-lg border border-outline-variant/60 text-on-surface font-label-md hover:bg-surface-container transition-colors" onClick={closeModal} type="button">Hủy</button>
                <button className="px-5 py-2 rounded-lg bg-primary text-on-primary font-label-md hover:bg-primary-container transition-colors" onClick={nextStep} type="button">
                  {currentStep === 3 ? 'Hoàn tất & Lưu' : 'Tiếp tục'}
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Toast Notification Simulator */}
        <div className={`fixed bottom-6 right-6 z-50 transform transition-all duration-300 pointer-events-none ${toast.visible ? 'translate-y-0 opacity-100' : 'translate-y-20 opacity-0'}`}>
          <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-inverse-surface text-inverse-on-surface shadow-xl border border-outline-variant/20">
            <span className="material-symbols-outlined text-[#34D399]">check_circle</span>
            <div>
              <div className="font-label-md font-semibold">{toast.title}</div>
              <div className="font-body-sm text-surface-dim">{toast.desc}</div>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
};
