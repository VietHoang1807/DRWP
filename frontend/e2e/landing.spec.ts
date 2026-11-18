import { expect, test } from '@playwright/test';
import { shot } from './helpers';

test('trang giới thiệu: hero, đường duyệt, dấu đóng, nút đăng nhập', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hồ sơ đi đúng đường, đến đúng người.');
  await shot(page, 'landing-01-hero');

  // Cuộn tới 90% hành trình của act đường duyệt: dấu ĐÃ DUYỆT phải đã đóng xuống.
  await page.evaluate(() => {
    const el = document.getElementById('duong-duyet')!;
    window.scrollTo(0, el.offsetTop + (el.offsetHeight - window.innerHeight) * 0.9);
  });
  await page.waitForTimeout(600);
  await shot(page, 'landing-02-peak');
  const stampOpacity = await page.locator('.ld-stamp--peak').evaluate((el) => Number(getComputedStyle(el).opacity));
  expect(stampOpacity).toBeGreaterThan(0.5);

  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await page.waitForTimeout(600);
  await shot(page, 'landing-03-close');
  await page.getByTestId('close-login').click();
  await expect(page).toHaveURL(/\/login/);
  expect(errors).toEqual([]);
});

test('trang giới thiệu: reduced motion vẫn đọc được đủ nội dung', async ({ browser }) => {
  const ctx = await browser.newContext({ reducedMotion: 'reduce', viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  await page.goto('/');
  await page.locator('#duong-duyet').scrollIntoViewIfNeeded();
  await expect(page.locator('.ld-stamp--peak')).toHaveCSS('opacity', '1');
  await ctx.close();
});

test('trang giới thiệu trên điện thoại', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  const page = await ctx.newPage();
  await page.goto('/');
  await page.screenshot({ path: 'e2e/screenshots/landing-mobile.png' });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await ctx.close();
});
