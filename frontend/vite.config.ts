import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Proxy tới business-app khi phát triển: tránh CORS, backend không cần sửa.
const backend = process.env.DRWP_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/dev': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/dev': { target: backend, changeOrigin: true },
    },
  },
});
