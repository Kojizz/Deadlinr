import { expect, test as setup } from "@playwright/test";

const AUTH_FILE = "e2e/.auth/user.json";

// Registers a fresh user through the UI and stores the session (httpOnly
// cookies included) so every spec runs authenticated against real servers.
setup("authenticate", async ({ page, request }) => {
  const backend = process.env.E2E_BACKEND_URL ?? "http://localhost:18080";
  const spec = await (await request.get(`${backend}/v3/api-docs`)).json();
  expect(spec.paths, "Rebuild/restart the backend: the running API must include F0 search and the work API")
    .toHaveProperty("/api/search");
  expect(spec.paths).toHaveProperty("/api/work-items");
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 10000)}@test.com`;

  await page.goto("/register");
  await page.getByLabel("Full name").fill("E2E User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("secret123");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  await page.context().storageState({ path: AUTH_FILE });
});
