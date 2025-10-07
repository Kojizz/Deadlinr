import { expect, test, type Page } from "@playwright/test";

async function accountsFixture(page: Page, options: { onlySocial?: boolean; failDisconnect?: boolean } = {}) {
  const linked = { google: true, github: false };
  const writes: string[] = [];
  let failDisconnect = !!options.failDisconnect;
  await page.route("**/*", async route => {
    const url = new URL(route.request().url());
    if (url.origin !== "http://localhost:3105") return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (url.pathname === "/api/users/me") return json({ id: 1, fullName: "Test Student", email: "test@example.test", role: "STUDENT" });
    if (url.pathname === "/api/auth/oauth2/accounts") return json({ linked, availability: { google: true, github: true }, passwordLoginEnabled: !options.onlySocial });
    if (url.pathname === "/api/auth/oauth2/accounts/google" && route.request().method() === "DELETE") {
      writes.push("disconnect-google");
      if (failDisconnect) return json({ message: "Disconnect failed" }, 503);
      linked.google = false;
      return route.fulfill({ status: 204 });
    }
    if (url.pathname === "/api/auth/oauth2/accounts/github/link") {
      writes.push("connect-github");
      return json({ ticket: "a".repeat(43) });
    }
    if (url.pathname === "/api/auth/oauth2/authorization/github") return route.fulfill({ contentType: "text/html", body: "<html lang='en'><title>Test provider</title><h1>Connect test GitHub</h1></html>" });
    if (url.pathname === "/api/settings") return json({ urgentDays: 3, escalationCap: 3, weeklyGoal: 5, timeZone: "Asia/Almaty", digestTime: "09:00", dailyDigest: true, weeklyReview: false, emailDigest: false });
    if (url.pathname === "/api/workspace/summary") return json({ totalPages: 0, notes: 0, tasks: 0, events: 0, openTasks: 0, overdue: 0, dueToday: 0, dueThisWeek: 0, createdThisWeek: 0, trashCount: 0, storageUsed: 0 });
    if (url.pathname === "/api/data/export/counts") return json({ tasks: 0, pages: 0, attachments: 0 });
    if (url.pathname === "/api/history") return json({ canUndo: false, canRedo: false });
    return json([]);
  });
  await page.goto("/settings");
  const section = page.getByRole("region", { name: "Connected accounts" });
  await expect(section.getByRole("button", { name: "Disconnect Google", exact: true })).toBeVisible();
  return { section, writes, recover: () => { failDisconnect = false; } };
}

test("connecting an account obtains a server ticket before starting OAuth", async ({ page }) => {
  const { section, writes } = await accountsFixture(page);
  await section.getByRole("button", { name: "Connect GitHub", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/api/auth/oauth2/authorization/github\\?link=${"a".repeat(43)}$`));
  await expect(page.getByRole("heading", { name: "Connect test GitHub" })).toBeVisible();
  expect(writes).toEqual(["connect-github"]);
});

test("failed disconnect retains the account and can be retried", async ({ page }) => {
  const state = await accountsFixture(page, { failDisconnect: true });
  await state.section.getByRole("button", { name: "Disconnect Google", exact: true }).click();
  await expect(page.getByText("Disconnect failed", { exact: true })).toBeVisible();
  await expect(state.section.getByRole("button", { name: "Disconnect Google", exact: true })).toBeEnabled();
  state.recover();
  await state.section.getByRole("button", { name: "Disconnect Google", exact: true }).click();
  await expect(state.section.getByRole("button", { name: "Connect Google", exact: true })).toBeVisible();
  expect(state.writes).toEqual(["disconnect-google", "disconnect-google"]);
});

test("mobile provider-only accounts keep their last sign-in method", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const { section, writes } = await accountsFixture(page, { onlySocial: true });
  await expect(section.getByRole("button", { name: "Disconnect Google", exact: true })).toBeDisabled();
  await expect(section.getByRole("button", { name: "Connect GitHub", exact: true })).toBeEnabled();
  await expect(section).toContainText("Keep at least one provider connected");
  expect(await section.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  expect(writes).toEqual([]);
});
