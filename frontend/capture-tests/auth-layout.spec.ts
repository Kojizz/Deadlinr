import { expect, test, type Page } from "@playwright/test";

async function authFixture(page: Page, options: { failures?: number; hold?: boolean; social?: boolean; socialFailure?: boolean } = {}) {
  const writes: { path: string; body: unknown }[] = [];
  const external: string[] = [];
  const user = { id: 1, fullName: "Test Student", email: "student@example.test", role: "STUDENT" };
  let authenticated = false;
  let failures = options.failures ?? 0;
  let release = () => {};
  const pending = options.hold ? new Promise<void>(resolve => { release = resolve; }) : Promise.resolve();

  await page.route("**/*", async route => {
    const url = new URL(route.request().url());
    if (url.origin !== "http://localhost:3105") {
      external.push(url.origin);
      return route.abort();
    }
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (url.pathname === "/api/users/me") return authenticated ? json(user) : json({ message: "Sign in required" }, 401);
    if (url.pathname === "/api/auth/refresh") return json({ message: "Sign in required" }, 401);
    if (url.pathname === "/api/auth/oauth2/providers") return json({ google: !!options.social, github: !!options.social });
    if (url.pathname.startsWith("/api/auth/oauth2/authorization/")) {
      if (options.socialFailure) return route.fulfill({ status: 302, headers: { location: "/login?oauthError=failed" }, body: "" });
      const provider = url.pathname.split("/").pop();
      // Local consent fixture; real provider redirects are covered by Spring tests.
      return route.fulfill({ contentType: "text/html", body: `<html lang="en"><title>Test provider</title><a href="/api/auth/oauth2/callback/${provider}?code=test-code&state=test-state">Authorize test ${provider}</a></html>` });
    }
    if (url.pathname.startsWith("/api/auth/oauth2/callback/")) {
      authenticated = true;
      return route.fulfill({ status: 302, headers: { location: "/dashboard", "set-cookie": "deadlinr_access=test-local-session; Path=/; HttpOnly; SameSite=Lax" }, body: "" });
    }
    if (url.pathname === "/api/auth/login" || url.pathname === "/api/auth/register") {
      writes.push({ path: url.pathname, body: route.request().postDataJSON() });
      await pending;
      if (failures-- > 0) return json({ message: "Please check your details and try again." }, 400);
      authenticated = true;
      return json({ user });
    }
    if (url.pathname === "/api/workspace/summary") return json({ totalPages: 0, notes: 0, tasks: 0, events: 0, openTasks: 0, overdue: 0, dueToday: 0, dueThisWeek: 0, createdThisWeek: 0, trashCount: 0, storageUsed: 0 });
    if (url.pathname === "/api/history") return json({ canUndo: false, canRedo: false });
    return json([]);
  });
  return { writes, external, release };
}

for (const route of ["login", "register"] as const) {
  for (const theme of ["light", "dark"] as const) {
    for (const width of [1440, 390]) {
      test(`${route} ${theme} at ${width}px keeps controls visible without horizontal scroll`, async ({ page }) => {
        const state = await authFixture(page);
        await page.addInitScript(theme => localStorage.setItem("theme", theme), theme);
        await page.emulateMedia({ reducedMotion: "reduce" });
        await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
        await page.goto(`/${route}`);
        await expect(page.getByRole("heading", { name: route === "login" ? "Welcome back" : "Create your workspace", exact: true })).toBeVisible();
        await expect(page.locator("html")).toHaveClass(theme === "dark" ? /dark/ : /light/);
        for (const provider of ["Google", "GitHub"]) {
          const button = page.getByRole("button", { name: `Continue with ${provider}`, exact: true });
          await expect(button).toBeVisible();
          await expect(button).toBeEnabled();
          await expect(button).toHaveAttribute("aria-describedby", "social-auth-hint");
        }
        await expect(page.getByText("Google and GitHub need to be configured on this server. Email sign-in is available.")).toBeVisible();
        await expect(page.getByRole("link", { name: "Create an account", exact: true })).toHaveCount(0);
        await expect(page.getByText("Tasks · Pages · Calendar", { exact: true })).toHaveCount(0);
        await expect(page.getByRole("button", { name: /Apple/ })).toHaveCount(0);
        await expect(page.getByRole("img", { name: "Deadlinr", exact: true }).filter({ visible: true })).toHaveCount(1);

        const password = page.getByLabel("Password", { exact: true });
        await password.fill("private-test-password");
        await expect(password).toHaveAttribute("type", "password");
        const reveal = page.getByRole("button", { name: "Show password", exact: true });
        await reveal.focus();
        await page.keyboard.press("Enter");
        await expect(password).toHaveAttribute("type", "text");
        await expect(page.getByRole("button", { name: "Hide password", exact: true })).toHaveAttribute("aria-pressed", "true");
        await page.keyboard.press("Enter");
        await expect(password).toHaveAttribute("type", "password");
        await password.fill("");
        await password.blur();
        const submit = page.getByRole("button", { name: route === "login" ? "Sign in" : "Create account", exact: true });
        await submit.scrollIntoViewIfNeeded();
        await expect(submit).toBeInViewport();
        expect((await submit.boundingBox())!.height).toBeGreaterThanOrEqual(44);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.locator("img:visible").evaluateAll(images => Promise.all(images.map(image => (image as HTMLImageElement).decode())));
        await page.screenshot({ path: `test-results/screenshots/auth-${route}-${width}-${theme}.png`, fullPage: true, animations: "disabled", style: "nextjs-portal { display: none; }" });

        await page.getByRole("link", { name: route === "login" ? "Create one" : "Sign in", exact: true }).last().click();
        await expect(page).toHaveURL(route === "login" ? /\/register$/ : /\/login$/);
        expect(state.writes).toEqual([]);
        expect(state.external).toEqual([]);
      });
    }
  }
}

test("email login preserves its request and dashboard redirect while blocking repeat submits", async ({ page }) => {
  const state = await authFixture(page, { hold: true });
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("student@example.test");
  await page.getByLabel("Password", { exact: true }).fill("private-test-password");
  await page.getByLabel("Password", { exact: true }).press("Enter");
  const form = page.getByRole("form", { name: "Sign in with email" });
  await expect(form).toHaveAttribute("aria-busy", "true");
  await expect(page.getByRole("button", { name: "Signing in…" })).toBeDisabled();
  await expect(page.getByLabel("Email", { exact: true })).toBeDisabled();
  // A second submit event must not issue another login while the first is pending.
  await form.dispatchEvent("submit");
  expect(state.writes).toEqual([{ path: "/api/auth/login", body: { email: "student@example.test", password: "private-test-password" } }]);
  state.release();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Welcome back, Test" })).toBeVisible();
  expect(state.external).toEqual([]);
});

for (const provider of ["Google", "GitHub"] as const) {
  test(`${provider} starts server OAuth and returns to the authenticated workspace`, async ({ page }) => {
    const state = await authFixture(page, { social: true });
    await page.goto("/login");
    const start = page.getByRole("button", { name: `Continue with ${provider}`, exact: true });
    await expect(start).toBeEnabled();
    const navigation = page.waitForRequest(request => request.url().includes(`/api/auth/oauth2/authorization/${provider.toLowerCase()}`));
    await start.click();
    expect((await navigation).isNavigationRequest()).toBe(true);
    await page.getByRole("link", { name: `Authorize test ${provider.toLowerCase()}` }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole("heading", { name: "Welcome back, Test" })).toBeVisible();
    expect(state.writes).toEqual([]);
    expect(state.external).toEqual([]);
  });
}

test("missing provider configuration and failed callbacks explain recovery without losing local login", async ({ page }) => {
  const state = await authFixture(page);
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("student@example.test");
  await page.getByRole("button", { name: "Continue with Google", exact: true }).click();
  await expect(page.getByLabel("Social sign-in").getByRole("alert")).toContainText("Google sign-in is not configured");
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue("student@example.test");
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeEnabled();
  expect(state.writes).toEqual([]);
  await page.goto("/login?oauthError=cancelled");
  await expect(page.getByLabel("Social sign-in").getByRole("alert")).toContainText("Sign-in was cancelled");
  expect(state.external).toEqual([]);
});

test("a failed OAuth redirect returns a safe error with an email sign-in fallback", async ({ page }) => {
  const state = await authFixture(page, { social: true, socialFailure: true });
  await page.goto("/register");
  await page.getByRole("button", { name: "Continue with GitHub", exact: true }).click();
  await expect(page).toHaveURL(/oauthError=failed/);
  await expect(page.getByLabel("Social sign-in").getByRole("alert")).toContainText("Social sign-in could not be completed");
  await expect(page.getByRole("form", { name: "Sign in with email" })).toBeVisible();
  expect(state.writes).toEqual([]);
  expect(state.external).toEqual([]);
});

test("registration validates locally, retains failed input, and retries with the existing payload", async ({ page }) => {
  const state = await authFixture(page, { failures: 1 });
  await page.goto("/register");
  await page.getByLabel("Full name", { exact: true }).fill("Test Student");
  await page.getByLabel("Email", { exact: true }).fill("student@example.test");
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  expect(state.writes).toEqual([]);
  await page.getByLabel("Password", { exact: true }).fill("private-test-password");
  await page.getByLabel("Group (optional)", { exact: true }).fill("CS-101");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page.getByRole("form", { name: "Create an account with email" }).getByRole("alert")).toHaveText("Please check your details and try again.");
  await expect(page.getByLabel("Full name", { exact: true })).toHaveValue("Test Student");
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("private-test-password");
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute("aria-describedby", "auth-error");
  await expect(page.getByRole("button", { name: "Create account", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  const expected = { path: "/api/auth/register", body: { fullName: "Test Student", email: "student@example.test", password: "private-test-password", group: "CS-101" } };
  expect(state.writes).toEqual([expected, expected]);
  expect(state.external).toEqual([]);
});
