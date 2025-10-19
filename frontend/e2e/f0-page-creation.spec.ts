import { test, expect, createPage, expectTitleSelected } from "./fixtures";

test("a root page is revealed in a long tree and survives reload", async ({ page }) => {
  for (let i = 0; i < 32; i++) {
    expect((await page.request.post("/api/pages", { data: { title: `Existing ${i}` } })).status()).toBe(201);
  }
  await page.goto("/dashboard");
  await expect(page.locator("[data-page-id]")).toHaveCount(32);
  const created = await createPage(page, () => page.getByRole("button", { name: "New page", exact: true }).click());
  const row = page.locator(`[data-page-id="${created.id}"]`);
  await expect(row).toBeInViewport();
  await expectTitleSelected(page);
  await page.reload();
  await expect(row).toBeInViewport();
});

test("a child of a collapsed parent appears immediately and after reload", async ({ page }) => {
  const parent = await (await page.request.post("/api/pages", { data: { title: "Parent" } })).json();
  await page.request.post("/api/pages", { data: { title: "Existing child", parentId: parent.id } });
  await page.goto("/dashboard");
  const parentRow = page.locator(`[data-page-id="${parent.id}"]`);
  await parentRow.getByRole("button", { name: /^Collapse/ }).click();
  await parentRow.hover();
  const child = await createPage(page, () => parentRow.getByRole("button", { name: "Add child page" }).click());
  const row = page.locator(`[data-page-id="${child.id}"]`);
  await expect(row).toBeInViewport();
  await expectTitleSelected(page);
  await page.reload();
  await expect(row).toBeInViewport();
  const tree = await (await page.request.get("/api/pages/tree")).json();
  expect(tree[0].children.map((node: { id: number }) => node.id)).toContain(child.id);
});

test("optimistic placeholder is replaced even while tree refresh is held", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page.getByRole("tree", { name: "Pages" })).toHaveCount(1);
  let releaseCreate!: () => void;
  const createGate = new Promise<void>((resolve) => { releaseCreate = resolve; });
  let releaseTree!: () => void;
  const treeGate = new Promise<void>((resolve) => { releaseTree = resolve; });
  await page.route("**/api/pages/tree", async (route) => { await treeGate; await route.continue(); });
  await page.route("**/api/pages", async (route) => { await createGate; await route.continue(); });
  try {
    await page.getByRole("button", { name: "New page", exact: true }).click();
    await expect(page.locator('[data-page-id][aria-busy="true"]')).toHaveCount(1);
    const response = page.waitForResponse((res) => res.url().endsWith("/api/pages") && res.request().method() === "POST");
    releaseCreate();
    const created = await (await response).json();
    await expect(page.locator(`[data-page-id="${created.id}"]`)).toBeInViewport();
    await expect(page.locator('[data-page-id][aria-busy="true"]')).toHaveCount(0);
  } finally {
    releaseCreate();
    releaseTree();
    await page.unrouteAll({ behavior: "wait" });
  }
});

test("failed concurrent create removes only its placeholder", async ({ page }) => {
  await page.goto("/dashboard");
  await expect(page.getByRole("tree", { name: "Pages" })).toHaveCount(1);
  let releaseFailure!: () => void;
  const gate = new Promise<void>((resolve) => { releaseFailure = resolve; });
  let calls = 0;
  await page.route("**/api/pages", async (route) => {
    if (++calls === 1) {
      await gate;
      await route.fulfill({ status: 500, contentType: "application/json", body: '{"message":"Test failure"}' });
    } else await route.continue();
  });
  try {
    const button = page.getByRole("button", { name: "New page", exact: true });
    await button.click();
    await expect(page.locator('[data-page-id][aria-busy="true"]')).toHaveCount(1);
    const created = await createPage(page, () => button.click());
    await expect(page.locator(`[data-page-id="${created.id}"]`)).toBeVisible();
    releaseFailure();
    await expect(page.locator('[data-page-id][aria-busy="true"]')).toHaveCount(0);
    await expect(page.locator(`[data-page-id="${created.id}"]`)).toBeVisible();
    expect((await (await page.request.get("/api/pages/tree")).json()).map((node: {id: number}) => node.id)).toEqual([created.id]);
  } finally {
    releaseFailure();
    await page.unrouteAll({ behavior: "wait" });
  }
});
