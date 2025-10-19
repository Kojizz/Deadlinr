import { test as base, expect, type Page } from "@playwright/test";

// Every test runs against a freshly registered user so data never leaks
// between tests (the shared setup session only populates the initial
// storageState; we log out of it and register our own account).
export const test = base.extend({
  page: async ({ page }, use) => {
    await page.context().clearCookies();
    await page.goto("/register");
    const email = `f0-${Date.now()}-${Math.random()}@test.com`;
    await page.getByLabel("Full name").fill("F0 Test");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password").fill("secret123");
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await use(page);
  },
});
export { expect };

export async function createPage(page: Page, action: () => Promise<void>) {
  const response = page.waitForResponse((res) => res.url().endsWith("/api/pages") && res.request().method() === "POST");
  await action();
  const result = await response;
  expect(result.status()).toBe(201);
  const created = await result.json();
  await expect(page).toHaveURL(new RegExp(`/pages/${created.id}$`));
  return created as { id: number };
}

export async function expectTitleSelected(page: Page) {
  const title = page.getByLabel("Page title");
  await expect(title).toBeFocused();
  await expect(title).toHaveValue("Untitled");
  await expect.poll(() => title.evaluate((input: HTMLTextAreaElement) => [input.selectionStart, input.selectionEnd]))
    .toEqual([0, 8]);
}