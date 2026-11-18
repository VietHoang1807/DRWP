import { expect, test } from '@playwright/test';
import { loginAs, requireBackend, shot } from './helpers';

/** Luồng nghiệp vụ chính trên dữ liệu `npm run seed`: nộp → trưởng phòng duyệt → giám đốc duyệt. */
test.describe.serial('hồ sơ nghỉ phép 2 cấp duyệt', () => {
  let code = '';

  test.beforeEach(async ({ page }) => { await requireBackend(page); });

  test('alice lập và nộp hồ sơ 8 ngày', async ({ page }) => {
    await loginAs(page, 'alice');
    await shot(page, 'app-01-dashboard-alice');
    await page.goto('/app/records/new');
    await page.getByTestId('pick-form-LEAVE').click();
    await page.getByLabel('Họ và tên').fill('Alice Nguyễn');
    await page.getByLabel('Loại nghỉ').selectOption('Nghỉ phép năm');
    await page.getByLabel('Từ ngày').fill('2026-12-14');
    await page.getByLabel('Số ngày').fill('8');
    await page.getByLabel('Lý do').fill('Kiểm thử e2e');
    await shot(page, 'app-02-record-new');
    await page.getByTestId('submit-record').click();
    await page.waitForURL(/\/app\/records\/[0-9a-f-]{36}/);
    code = (await page.locator('h1').first().textContent())?.trim() ?? '';
    await expect(page.getByText(/Đã nộp|Đang xử lý/).first()).toBeVisible();
    await shot(page, 'app-03-record-detail');
  });

  test('bob duyệt cấp 1', async ({ page }) => {
    await loginAs(page, 'bob');
    await page.goto('/app/tasks');
    await page.getByText(code).first().click();
    await shot(page, 'app-04-inbox-bob');
    await page.getByTestId('transition-approve').click();
    await page.getByRole('dialog').getByRole('textbox').fill('Đồng ý');
    await page.getByTestId('confirm-transition').click();
    await expect(page.getByText(code)).toHaveCount(0, { timeout: 15_000 });
  });

  test('carol duyệt cấp 2, hồ sơ thành Đã phê duyệt', async ({ page }) => {
    await loginAs(page, 'carol');
    await page.goto('/app/tasks');
    await expect(page.getByText(code).first()).toBeVisible({ timeout: 15_000 });
    await page.getByText(code).first().click();
    await page.getByTestId('transition-approve').click();
    await page.getByTestId('confirm-transition').click();
    await page.goto(`/app/records?code=${encodeURIComponent(code)}`);
    await expect(page.getByText('Đã phê duyệt').first()).toBeVisible({ timeout: 15_000 });
    await shot(page, 'app-05-records-carol');
  });
});

test.describe('màn hình quản trị', () => {
  test.beforeEach(async ({ page }) => { await requireBackend(page); });

  test('admin-demo: biểu mẫu, quy trình, vai trò, thành viên, nhật ký', async ({ page }) => {
    await loginAs(page, 'admin-demo');
    for (const [path, name] of [['/app/forms/LEAVE', 'form-builder'], ['/app/workflows/LEAVE_APPROVAL', 'workflow-designer'],
      ['/app/roles', 'roles'], ['/app/members', 'members'], ['/app/audit', 'audit'], ['/app/batch-jobs', 'coming-soon']] as const) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      await expect(page.locator('.sk')).toHaveCount(0);
      await shot(page, `admin-${name}`);
    }
  });

  test('platform-root: danh sách đơn vị', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-platform-root').click();
    await page.goto('/app/platform/tenants');
    await expect(page.getByText('demo', { exact: true })).toBeVisible();
    await shot(page, 'platform-tenants');
  });
});
