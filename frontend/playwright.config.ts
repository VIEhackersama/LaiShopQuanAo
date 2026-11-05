import { defineConfig, devices } from '@playwright/test';
const channel = process.env.PLAYWRIGHT_CHANNEL ?? 'msedge';
export default defineConfig({ testDir: './tests', fullyParallel: false, workers: 1, timeout: 45000, expect: { timeout: 10000 }, use: { baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure' }, projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'], channel } }, { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium', channel } }], reporter: [['list'], ['html', { open: 'never' }]] });
