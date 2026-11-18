import { expect, type Page, test } from '@playwright/test';

export const shot = (page: Page, name: string) => page.screenshot({ path: `e2e/screenshots/${name}.png`, fullPage: false });

/** Bỏ qua test nếu backend (profile dev) chưa chạy hoặc chưa seed. */
export async function requireBackend(page: Page) {
  const res = await page.request.post('/dev/token', { data: { subject: 'alice' }, failOnStatusCode: false }).catch(() => null);
  test.skip(!res || !res.ok(), 'Backend chưa chạy (mvn -pl business-app spring-boot:run -Dspring-boot.run.profiles=dev) hoặc chưa npm run seed');
}

/** Đăng nhập bằng tài khoản mẫu và vào đơn vị demo. */
export async function loginAs(page: Page, subject: string) {
  await page.goto('/login');
  await page.getByTestId(`login-${subject}`).click();
  await page.waitForURL(/\/(select-tenant|app)/);
  if (page.url().includes('/select-tenant')) await page.getByTestId('tenant-demo').click();
  await page.waitForURL(/\/app/);
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toBeVisible();
}
