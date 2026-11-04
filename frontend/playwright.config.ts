import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: 'http://127.0.0.1:8091',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    launchOptions: {
      executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
    },
    screenshot: 'only-on-failure',
  },
  webServer: {
    command:
      'java -jar ../backend/target/fintry.jar --spring.profiles.active=demo --server.port=8091 "--spring.datasource.url=jdbc:h2:mem:workspace-e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=30000;DB_CLOSE_DELAY=-1"',
    url: 'http://127.0.0.1:8091/actuator/health/readiness',
    timeout: 120000,
    reuseExistingServer: !process.env.CI,
  },
});
