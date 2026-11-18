import type { WorkflowSpec } from '../../api/types';

/** Mẫu khởi đầu: trưởng phòng duyệt, nếu > 5 ngày thì giám đốc duyệt. Sửa vai trò/điều kiện cho phù hợp. */
export const twoLevelTemplate: WorkflowSpec = {
  initialState: 'MANAGER_REVIEW',
  states: [
    { code: 'MANAGER_REVIEW', name: 'Trưởng phòng duyệt', type: 'TASK', assignment: { type: 'ROLE', value: 'MANAGER' }, timeout: 'P3D', onTimeout: 'escalate' },
    { code: 'CHECK', name: 'Kiểm tra điều kiện', type: 'DECISION' },
    { code: 'DIRECTOR_REVIEW', name: 'Giám đốc duyệt', type: 'TASK', assignment: { type: 'ROLE', value: 'DIRECTOR' } },
    { code: 'APPROVED', name: 'Đã duyệt', type: 'END', outcome: 'APPROVED' },
    { code: 'REJECTED', name: 'Từ chối', type: 'END', outcome: 'REJECTED' },
    { code: 'RETURNED', name: 'Trả lại', type: 'END', outcome: 'RETURNED' },
  ],
  transitions: [
    { code: 'approve', from: 'MANAGER_REVIEW', to: 'CHECK', permission: 'APPROVE', label: 'Đồng ý' },
    { code: 'reject', from: 'MANAGER_REVIEW', to: 'REJECTED', permission: 'REJECT', commentRequired: true, label: 'Từ chối' },
    { code: 'return', from: 'MANAGER_REVIEW', to: 'RETURNED', permission: 'REJECT', commentRequired: true, label: 'Trả lại bổ sung' },
    { code: 'escalate', from: 'MANAGER_REVIEW', to: 'DIRECTOR_REVIEW', permission: 'APPROVE', label: 'Chuyển giám đốc' },
    { code: 'long', from: 'CHECK', to: 'DIRECTOR_REVIEW', guard: { field: 'days', op: 'gt', value: 5 }, label: 'Trên 5 ngày' },
    { code: 'short', from: 'CHECK', to: 'APPROVED', label: 'Còn lại' },
    { code: 'approve', from: 'DIRECTOR_REVIEW', to: 'APPROVED', permission: 'APPROVE', label: 'Phê duyệt' },
    { code: 'reject', from: 'DIRECTOR_REVIEW', to: 'REJECTED', permission: 'REJECT', commentRequired: true, label: 'Từ chối' },
  ],
};
