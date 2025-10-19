import { test, expect, createPage, expectTitleSelected } from "./fixtures";

test("desktop and collapsed sidebar: New page stays clickable", async ({ page }) => {
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Toggle sidebar" }).click();
  const button = page.getByRole("button", { name: "New page", exact: true });
  await expect(button).toBeVisible();
  const created = await createPage(page, () => button.click());
  await expect(page.locator(`[data-page-id="${created.id}"]`)).toBeInViewport();
  await expectTitleSelected(page);
  await createPage(page, () => button.click());
  await expectTitleSelected(page);
});

test("mobile drawer: create closes navigation and selects the title", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Open menu" }).click();
  const button = page.getByRole("button", { name: "New page", exact: true });
  await expect(button).toBeVisible();
  const created = await createPage(page, () => button.click());
  await expect(page.getByRole("dialog", { name: "Navigation" })).not.toBeVisible();
  await expectTitleSelected(page);
  await page.getByRole("button", { name: "Open menu" }).click();
  await expect(page.getByRole("dialog").locator(`[data-page-id="${created.id}"]`)).toBeInViewport();
});

test("keyboard shortcut creates a page after the app mounts", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page.getByRole("button", { name: "New page", exact: true })).toBeVisible();
  await createPage(page, () => page.keyboard.press("Control+Alt+N"));
  await expectTitleSelected(page);
});

test("empty state and command palette create pages", async ({ page }) => {
  await page.goto("/dashboard");
  await createPage(page, () => page.getByRole("button", { name: "New page", exact: true }).click());
  await page.keyboard.press("Control+Shift+K");
  await createPage(page, () => page.getByRole("option", { name: /New page/ }).click());
  await expectTitleSelected(page);
});
