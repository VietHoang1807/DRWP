/** Chuỗi hiển thị tập trung để có thể dịch sau này. */
export const t = {
  app: 'DRWP',
  appLong: 'Nền tảng Hồ sơ & Quy trình động',
  recordStatus: {
    DRAFT: 'Nháp',
    SUBMITTED: 'Đã nộp',
    IN_PROGRESS: 'Đang xử lý',
    APPROVED: 'Đã phê duyệt',
    REJECTED: 'Bị từ chối',
    RETURNED: 'Trả lại bổ sung',
    CANCELLED: 'Đã huỷ',
  } as Record<string, string>,
  versionStatus: { DRAFT: 'Bản nháp', PUBLISHED: 'Đã phát hành', DEPRECATED: 'Ngừng dùng' } as Record<string, string>,
  tenantStatus: { PROVISIONING: 'Đang khởi tạo', ACTIVE: 'Hoạt động', SUSPENDED: 'Tạm dừng', ARCHIVED: 'Lưu trữ' } as Record<string, string>,
  taskStatus: { OPEN: 'Chờ xử lý', COMPLETED: 'Đã xử lý', CANCELLED: 'Đã huỷ' } as Record<string, string>,
  instanceStatus: { RUNNING: 'Đang chạy', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã huỷ' } as Record<string, string>,
  outcome: { APPROVED: 'Phê duyệt', REJECTED: 'Từ chối', RETURNED: 'Trả lại', CANCELLED: 'Huỷ' } as Record<string, string>,
  action: {
    VIEW: 'Xem', CREATE: 'Tạo', UPDATE: 'Sửa', DELETE: 'Xoá', APPROVE: 'Duyệt', REJECT: 'Từ chối',
    EXPORT: 'Xuất', ASSIGN: 'Giao việc', SHARE: 'Chia sẻ', CONFIGURE: 'Cấu hình', AUDIT: 'Kiểm toán', MANAGE: 'Quản trị',
  } as Record<string, string>,
  fieldType: {
    TEXT: 'Văn bản ngắn', TEXTAREA: 'Văn bản dài', EMAIL: 'Email', NUMBER: 'Số', INTEGER: 'Số nguyên', BOOLEAN: 'Có / Không',
    DATE: 'Ngày', DATETIME: 'Ngày giờ', SELECT: 'Chọn một', MULTISELECT: 'Chọn nhiều', FILE: 'Tệp đính kèm', USER: 'Người dùng',
  } as Record<string, string>,
  stateType: { TASK: 'Công việc', DECISION: 'Rẽ nhánh', END: 'Kết thúc' } as Record<string, string>,
};

export const label = (map: Record<string, string>, key?: string | null) => (key ? map[key] ?? key : '—');
