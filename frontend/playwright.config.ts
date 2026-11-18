import { defineConfig, devices } from '@playwright/test';

/**
 * npm run e2e  : tự chạy Vite dev (proxy /api, /dev → DRWP_BACKEND_URL, mặc định :8080).
 * Các test cần backend sẽ tự bỏ qua nếu không gọi được POST /dev/token.
 * Ảnh chụp xác minh lưu ở e2e/screenshots/.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e/report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'vi-VN',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
  ],
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
