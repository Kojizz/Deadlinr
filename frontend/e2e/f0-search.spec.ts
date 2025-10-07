import { test, expect, createPage } from "./fixtures";

test("newly renamed pages are found by prefix, typo and Cyrillic queries", async ({ page }) => {
  await page.goto("/dashboard");
  await createPage(page, () => page.getByRole("button", { name: "New page", exact: true }).click());
  const title = page.getByLabel("Page title");
  await title.fill("Machine Learning notes");
  await title.blur();
  await expect(page.getByRole("status").getByText("Saved")).toBeVisible();
  await page.keyboard.press("Control+Shift+K");
  const palette = page.getByTestId("palette");
  for (const query of ["mach", "machime"]) {
    await palette.getByRole("combobox", { name: "Search" }).fill(query);
    await expect(palette.getByRole("option", { name: /Machine Learning notes/ })).toBeVisible();
  }
  await page.keyboard.press("Escape");
  const saved = page.waitForResponse((res) => res.request().method() === "PATCH");
  await title.fill("Конспект по матанализу");
  await title.blur();
  expect((await saved).status()).toBe(200);
  await page.keyboard.press("Control+Shift+K");
  await palette.getByRole("combobox", { name: "Search" }).fill("матанал");
  await expect(palette.getByRole("option", { name: /Конспект по матанализу/ })).toBeVisible();
});

test("an empty cached search is invalidated after create and rename", async ({ page }) => {
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  const palette = page.getByTestId("palette");
  await palette.getByRole("combobox").fill("astrophysics");
  await expect(palette.getByText("No results", { exact: true })).toBeVisible();
  await createPage(page, () => palette.getByRole("option", { name: /New page/ }).click());
  const saved = page.waitForResponse((res) => res.request().method() === "PATCH");
  await page.getByLabel("Page title").fill("Astrophysics");
  await page.getByLabel("Page title").blur();
  expect((await saved).status()).toBe(200);
  await page.keyboard.press("Control+Shift+K");
  await palette.getByRole("combobox").fill("astrophysics");
  await expect(palette.getByRole("option", { name: /Astrophysics/ })).toBeVisible();
  await page.keyboard.press("Enter");
  await expect(palette).not.toBeVisible();
});

test("search errors are shown explicitly and modal focus stays inside", async ({ page }) => {
  await page.route("**/api/search?**", (route) => route.fulfill({ status: 503, contentType: "application/json", body: '{"message":"Unavailable"}' }));
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  const palette = page.getByTestId("palette");
  await palette.getByRole("combobox").fill("anything");
  await expect(palette.getByRole("alert")).toContainText("Search is unavailable");
  await expect(palette.getByText("No results", { exact: true })).toHaveCount(0);
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press("Tab");
    await expect.poll(() => palette.evaluate((element) => element.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "Search", exact: true })).toBeFocused();
});

test("groups pages and opens the page detail", async ({ page }) => {
  const created = await (await page.request.post("/api/pages", { data: { title: "Machine study notes" } })).json();
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  const palette = page.getByTestId("palette");
  await palette.getByRole("combobox").fill("mach");
  const pages = palette.getByRole("group", { name: "Pages", exact: true });
  await expect(pages).toBeVisible();
  await pages.getByRole("option", { name: /Machine study notes/ }).click();
  await expect(page).toHaveURL(new RegExp(`/pages/${created.id}$`));
  await expect(page.getByLabel("Page title")).toHaveValue("Machine study notes");
});

test("account changes clear cached tree and search results", async ({ page }) => {
  await page.request.post("/api/pages", { data: { title: "Private astrophysics" } });
  await page.goto("/dashboard");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await page.getByRole("combobox").fill("astrophysics");
  await expect(page.getByRole("option", { name: /Private astrophysics/ })).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "FT", exact: true }).click();
  await page.getByRole("menuitem", { name: "Log out", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  // Follow the in-app link to keep the root QueryProvider mounted across users.
  await page.getByRole("link", { name: "Create one" }).click();
  await page.getByLabel("Full name").fill("Second User");
  await page.getByLabel("Email").fill(`f0-second-${Date.now()}@test.com`);
  await page.getByLabel("Password").fill("secret123");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "New page", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await page.getByRole("combobox").fill("astrophysics");
  await expect(page.getByTestId("palette").getByText("No results", { exact: true })).toBeVisible();
  await expect(page.getByText("Private astrophysics")).toHaveCount(0);
});
