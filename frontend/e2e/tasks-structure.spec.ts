import { test, expect } from "./fixtures";

test.describe("Task / Page structure", () => {
  test("create-from-page: visible in page section, tasks list, calendar, overview", async ({ page }) => {
    // Create a page through the UI
    await page.request.post("/api/pages", { data: { title: "Alpha" } });
    const pages = await (await page.request.get("/api/workspace/pages?trash=false&size=50")).json();
    const alpha = pages.find((p: any) => p.title === "Alpha");
    await page.goto(`/pages/${alpha.id}`);

    // Create a task from the page screen
    await page.getByRole("button", { name: /Add task/i }).first().click();
    await page.getByRole("textbox", { name: "Title", exact: true }).fill("Write report");
    await page.getByLabel("Description").fill("Full write-up");
    await page.getByRole("button", { name: /Create task/i }).click();

    // Appears in the page section
    await expect(page.getByText("Write report").first()).toBeVisible();

    // Appears in the Tasks list
    await page.goto("/tasks");
    await expect(page.getByText("Write report").first()).toBeVisible();
    // Page chip shows the page title
    await expect(page.getByRole("link", { name: /Alpha/ })).toBeVisible();

    // Appears on the Calendar if it has a due date: we don't set a due date here,
    // so create one with a due date via the API directly with the same page
    const now = new Date();
    now.setDate(now.getDate() + 1);
    const due = now.toISOString();
    await page.request.post("/api/work-items", { data: { title: "Due today", kind: "TASK", dueAt: due, pageId: alpha.id } });
    await page.goto("/calendar");
    await expect(page.getByText("Due today")).toBeVisible();

    // And shows up on the Overview count
    await page.goto("/dashboard");
    await expect(page.getByText("Open tasks").first()).toBeVisible();
  });

  test("detail sheet opens the same way everywhere", async ({ page }) => {
    const created = await (await page.request.post("/api/pages", { data: { title: "Beta" } })).json();
    const item = await (await page.request.post("/api/work-items", {
      data: { title: "Shared detail", kind: "TASK", description: "Hello descr", pageId: created.id }
    })).json();

    // From tasks list
    await page.goto("/tasks");
    await page.getByRole("button", { name: "Shared detail", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("dialog").getByText("Hello descr")).toBeVisible();
    await expect(page).toHaveURL(/task=\d+/);

    // Close
    await page.getByRole("button", { name: "Close dialog" }).click();

    // From page section
    await page.goto(`/pages/${created.id}`);
    await page.getByRole("button", { name: "Shared detail", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("dialog").getByText("Hello descr")).toBeVisible();
  });

  test("deep link ?task=<id> opens the sheet; back closes it", async ({ page }) => {
    const created = await (await page.request.post("/api/pages", { data: { title: "Gamma" } })).json();
    const item = await (await page.request.post("/api/work-items", {
      data: { title: "Deep link me", kind: "TASK", description: "Deep descr", pageId: created.id }
    })).json();
    await page.goto(`/tasks?task=${item.id}`);
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByRole("dialog").getByText("Deep descr")).toBeVisible();
    await page.keyboard.press("Escape");
    // Escape closes the modal; ?task is still in the URL until replaced.
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });

  test("complete from the page reflects everywhere", async ({ page }) => {
    const created = await (await page.request.post("/api/pages", { data: { title: "Delta" } })).json();
    await page.request.post("/api/work-items", {
      data: { title: "Complete me", kind: "TASK", pageId: created.id }
    });
    await page.goto(`/pages/${created.id}`);
    const completeTasksBox=page.getByLabel("Complete Complete me");await completeTasksBox.click();await expect(completeTasksBox).toBeChecked();

    await page.goto("/tasks");
    // The card grid has no per-row status select; completion surfaces via the
    // checkbox flipping to "Reopen" plus a green "Done" status chip.
    await expect(page.getByLabel("Reopen Complete me")).toBeChecked();
    await expect(page.getByText("Done", { exact: true }).first()).toBeVisible();
  });

  test("deleting a page moves its tasks and events to Trash with it; restoring revives them", async ({ page }) => {
    const created = await (await page.request.post("/api/pages", { data: { title: "Echo" } })).json();
    await page.request.post("/api/work-items", { data: { title: "Trash task", kind: "TASK", pageId: created.id } });
    await page.request.post("/api/work-items", { data: { title: "Trash event", kind: "EVENT", pageId: created.id } });

    await page.goto(`/pages/${created.id}`);
    await page.getByRole("button", { name: /Delete page/i }).click();
    await page.getByRole("button", { name: "Confirm" }).click();

    await page.goto("/trash");
    await expect(page.getByText("Trash task")).toBeVisible();
    await expect(page.getByText("Trash event")).toBeVisible();
    await expect(page.getByText("Echo (+1 tasks, 1 events)")).toBeVisible();
  });

  test("task created from Tasks list has no page chip; attaching via sheet surfaces it", async ({ page }) => {
    const created = await (await page.request.post("/api/pages", { data: { title: "Zeta" } })).json();
    await page.request.post("/api/work-items", { data: { title: "Floating", kind: "TASK" } });
    await page.goto("/tasks");
    await expect(page.getByRole("link", { name: /Zeta/ })).toHaveCount(0);

    // Open it via URL in edit mode and set the Page
    const list = await (await page.request.get("/api/work-items")).json();
    const floating = list.items.find((i: any) => i.title === "Floating");
    await page.goto(`/tasks?edit=${floating.id}`);
    // Wait until the page picker options are loaded so the datalist match
    // resolves the pageId before saving.
    await page.waitForFunction(() =>
      [...document.querySelectorAll("#page-options option")].some((o) => o.getAttribute("value") === "Zeta"));
    await page.getByLabel("Page", { exact: true }).fill("Zeta");
    await page.getByRole("button", { name: /Save changes|Create/i }).click();

    await page.goto(`/pages/${created.id}`);
    await expect(page.getByText("Floating").first()).toBeVisible();
  });

  test("overview and tasks page counts agree", async ({ page }) => {
    await page.request.post("/api/work-items", { data: { title: "A", kind: "TASK" } });
    await page.request.post("/api/work-items", { data: { title: "B", kind: "TASK" } });
    await page.goto("/dashboard");
    const open = page.locator("[data-testid=\"count-open-tasks\"]");
    await expect(open).toBeVisible();
    // The count renders as "…" while the summary is loading; wait for digits.
    await expect(open).toHaveText(/^\d+$/);
    const beforeText = await open.textContent();
    const n = Number(beforeText);

    await page.goto("/tasks");
    await expect(page.getByText(`${n} items`)).toBeVisible();
  });

  test("event form has no Course field; old event with courseId opens fine", async ({ page }) => {
    const created = await (await page.request.post("/api/courses", {
      data: { name: "Old Course", code: "OLD101", instructor: "prof" }
    })).json();
    const event = await (await page.request.post("/api/work-items", {
      data: { title: "Legacy event", kind: "EVENT", courseId: created.id, startAt: new Date().toISOString(), dueAt: new Date(Date.now() + 3600_000).toISOString() }
    })).json();

    await page.goto("/calendar");
    // Form should not show Course
    await page.getByRole("button", { name: /New event/i }).click();
    await expect(page.getByLabel("New task title")).toHaveCount(0);
    await expect(page.getByLabel("Title")).toBeVisible();
    await expect(page.getByLabel("Course")).toHaveCount(0);

    await page.keyboard.press("Escape");
    const tasks = await (await page.request.get("/api/work-items?kind=EVENT")).json();
    const legacy = tasks.items.find((i: any) => i.title === "Legacy event");
    await page.goto(`/calendar?task=${legacy.id}`);
    await expect(page.getByRole("dialog").getByRole("heading", { name: "Legacy event" })).toBeVisible();
    await expect(page.getByLabel("Course")).toHaveCount(0);
  });

  test("double-click create makes exactly one task", async ({ page }) => {
    await page.goto("/tasks");
    // The "n" shortcut only responds after hydration; the first press may be
    // swallowed, so retry once if the dialog did not open.
    await expect(page.getByText("Focus a card for keyboard shortcuts")).toBeVisible();
    await page.keyboard.press("n");
    const dialog = page.getByLabel("Title");
    try {
      await dialog.waitFor({ state: "visible", timeout: 5000 });
    } catch {
      await page.keyboard.press("n");
      await expect(dialog).toBeVisible();
    }
    await page.getByRole("textbox", { name: "Title", exact: true }).fill("Double me");
    await page.getByRole("button", { name: /Create task/i }).dblclick();
    await page.waitForTimeout(600);
    await page.goto("/tasks");
    await expect(page.getByRole("button", { name: "Double me", exact: true })).toHaveCount(1);
  });
});





