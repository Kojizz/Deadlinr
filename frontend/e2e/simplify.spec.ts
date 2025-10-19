import { test, expect } from "./fixtures";

test.describe("Simplification", () => {
  test("retired routes redirect to /tasks", async ({ page }) => {
    for (const url of ["/today", "/inbox", "/someday", "/assignments"]) {
      await page.goto(url);
      await expect(page).toHaveURL(/\/tasks$/);
    }
    await page.goto("/logbook");
    await expect(page).toHaveURL(/\/tasks\?status=DONE/);
    await page.goto("/anytime");
    await expect(page).toHaveURL(/\/tasks\?due=nodate/);
    await page.goto("/upcoming");
    await expect(page).toHaveURL(/\/tasks\?from=/);
  });

  test("tasks page has none of the removed controls", async ({ page }) => {
    await page.goto("/tasks");
    await expect(page.getByLabel("Search list")).toBeVisible();
    await expect(page.getByLabel("Sort", { exact: true })).toBeVisible();
    await expect(page.getByPlaceholder("+ Add task")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Quick add", exact: true })).toBeVisible();
    await expect(page.getByLabel("Source filter")).toHaveCount(0);
    await expect(page.getByLabel("Course filter")).toHaveCount(0);
    await expect(page.getByText("Hard deadline only")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Save view" })).toHaveCount(0);
  });

  test("date range filter syncs to the URL", async ({ page }) => {
    await page.goto("/tasks");
    await page.getByRole("button", { name: "Date range filter" }).click();
    await page.getByRole("button", { name: "Today", exact: true }).click();
    await expect(page).toHaveURL(/from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/);
    await page.getByRole("button", { name: "Date range filter" }).click();
    await page.getByRole("dialog", { name: "Choose date range" }).getByRole("button", { name: "Clear", exact: true }).click();
    await page.getByRole("button", { name: "Date range filter" }).click();
    await page.getByRole("button", { name: "No date", exact: true }).click();
    await expect(page).toHaveURL(/due=nodate/);
  });

  test("bulk toolbar has no Course option", async ({ page }) => {
    await page.request.post("/api/work-items", { data: { title: "Bulk me", kind: "TASK" } });
    await page.goto("/tasks");
    await page.getByLabel("Select Bulk me").check();
    await expect(page.getByLabel("Bulk status")).toBeVisible();
    await expect(page.getByLabel("Bulk priority")).toBeVisible();
    await expect(page.getByLabel("Bulk due date")).toBeVisible();
    await expect(page.getByLabel("Bulk course")).toHaveCount(0);
  });

  test("+ New menu offers only Page, Task, Event", async ({ page }) => {
    await page.goto("/tasks");
    await page.getByRole("banner").getByLabel("Create options").click();
    await expect(page.getByRole("button", { name: /^Page$/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Task$/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Event$/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /Assignment|Birthday/ })).toHaveCount(0);
  });

  test("migrated assignments appear as tasks", async ({ page }) => {
    // V10 converted kind='ASSIGNMENT' rows to TASK; creating an assignment-typed
    // item must now be rejected by the API, and items with the old kind must
    // have become tasks.
    const res = await page.request.post("/api/work-items", { data: { title: "Old assignment", kind: "ASSIGNMENT" } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    const list = await page.request.get("/api/work-items");
    const body = await list.json();
    for (const item of body.items) expect(item.kind).not.toBe("ASSIGNMENT");
  });
});
