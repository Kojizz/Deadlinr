import { defineConfig, devices } from "@playwright/test";

// Keep e2e off the app (3000) and Grafana (3001). Override for an existing server.
const PORT = process.env.PORT ?? "3100";
const BASE_URL = `http://localhost:${PORT}`;
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? "http://localhost:18080";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
  },
  projects: [
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        storageState: "e2e/.auth/user.json",
      },
      dependencies: ["setup"],
    },
  ],
  webServer: [
    {
      command: process.env.BACKEND_CMD ?? "mvn -f ../backend/pom.xml spring-boot:run",
      url: `${BACKEND_URL}/actuator/health`,
      reuseExistingServer: !process.env.CI,
      timeout: 240_000,
      env: { LOGIN_RATE_CAPACITY: "1000", SERVER_PORT: new URL(BACKEND_URL).port || "80" },
    },
    {
      command: `npx next dev -p ${PORT}`,
      url: BASE_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      // Optional: point the e2e frontend at an alternative backend
      // (e.g. E2E_API_URL=http://localhost:8081/api). Defaults to the /api proxy.
      env: {
        NEXT_DIST_DIR: ".next-e2e",
        BACKEND_URL,
        ...(process.env.E2E_API_URL ? { NEXT_PUBLIC_API_URL: process.env.E2E_API_URL } : {}),
      },
    },
  ],
});
