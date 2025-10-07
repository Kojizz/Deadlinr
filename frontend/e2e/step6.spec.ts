import { test, expect } from "./fixtures";

// Step 6: task cards (Step 3 artifacts), read-only view vs edit flow, header
// wording, Task|Event switch, Calendar slot defaults, no Course field, and
// network request-count guards.
test.describe("Step 6: task cards, view/edit flow and header wording", () => {
  test("card grid shows description preview, status, priority, due, page chip and label", async ({ page }) => {
    const createdPage = await (await page.request.post("/api/pages", {
      data: { title: "CardHome" }
    })).json();
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(12, 0, 0, 0);
    await page.request.post("/api/work-items", {
      data: {
        title: "Card item",
        kind: "TASK",
        description: "A sufficiently long preview text for the card.",
        status: "IN_PROGRESS",
        priority: 2,
        dueAt: tomorrow.toISOString(),
        pageId: createdPage.id,
        labels: ["ml"]
      }
    });
    await page.goto("/tasks");
    const card = page.locator("article[data-item-id]").filter({ hasText: "Card item" });
    await expect(card).toBeVisible();
    await expect(card).toContainText("A sufficiently long preview text for the card.");
    await expect(card.getByText("In progress", { exact: true })).toBeVisible();
    await expect(card.getByText("P2 High", { exact: true })).toBeVisible();
    await expect(card.getByText(/Tomorrow/)).toBeVisible();
    await expect(card.getByRole("link", { name: "CardHome" })).toBeVisible();
    await expect(card.getByText("#ml", { exact: true })).toBeVisible();
    await expect(card.getByLabel("Complete Card item")).toBeVisible();
  });

  test("card click opens a read-only sheet; Edit opens the form and save updates the card", async ({ page }) => {
    const item = await (await page.request.post("/api/work-items", {
      data: { title: "Rename me", kind: "TASK", description: "Before edit" }
    })).json();
    await page.goto("/tasks");
    await page.getByRole("button", { name: "Rename me", exact: true }).click();
    await expect(page).toHaveURL(/task=\d+/);
    const sheet = page.getByRole("dialog");
    await expect(sheet.getByRole("heading", { name: "Rename me" })).toBeVisible();
    await expect(sheet.getByText("Before edit")).toBeVisible();
    // View sheet is read-only: no form controls.
    await expect(sheet.locator("input,select,textarea")).toHaveCount(0);

    await sheet.getByRole("button", { name: "Edit", exact: true }).click();
    await expect(page).toHaveURL(/edit=\d+/);
    const form = page.getByRole("dialog");
    const title = form.getByRole("textbox", { name: "Title", exact: true });
    await expect(title).toHaveValue("Rename me");
    await title.fill("Renamed item");
    await form.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Renamed item", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Rename me", exact: true })).toHaveCount(0);
  });

  test("page section sheet is read-only and Edit in Tasks routes to /tasks", async ({ page }) => {
    const createdPage = await (await page.request.post("/api/pages", {
      data: { title: "SectionHome" }
    })).json();
    await page.request.post("/api/work-items", {
      data: { title: "Section task", kind: "TASK", pageId: createdPage.id }
    });
    await page.goto("/pages/" + createdPage.id);
    await page.getByRole("button", { name: "Section task", exact: true }).click();
    const sheet = page.getByRole("dialog");
    await expect(sheet.getByRole("heading", { name: "Section task" })).toBeVisible();
    await expect(sheet.locator("input,select,textarea")).toHaveCount(0);
    await sheet.getByRole("button", { name: "Edit in Tasks" }).click();
    await expect(page).toHaveURL(/\/tasks\?edit=\d+/);
    await expect(
      page.getByRole("dialog").getByRole("textbox", { name: "Title", exact: true })
    ).toBeVisible();
  });

  test("page header offers one Add task or event button with a task default and no Course field", async ({ page }) => {
    const createdPage = await (await page.request.post("/api/pages", {
      data: { title: "HeaderHome" }
    })).json();
    await page.goto("/pages/" + createdPage.id);
    const headerButton = page.getByRole("button", { name: "Add task or event", exact: true });
    await expect(headerButton).toBeVisible();
    await headerButton.click();
    const sheet = page.getByLabel("New task");
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("tab", { name: "Task" })).toHaveAttribute("aria-selected", "true");
    await expect(sheet.getByLabel("Course")).toHaveCount(0);
  });

  test("create dialog switches between Task and Event without a Course field", async ({ page }) => {
    await page.goto("/tasks");
    await page.getByRole("banner").getByRole("button", { name: "New", exact: true }).click();
    let sheet = page.getByLabel("New task");
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("tab", { name: "Task" })).toHaveAttribute("aria-selected", "true");
    await expect(sheet.getByRole("button", { name: "Create task", exact: true })).toBeVisible();
    await expect(sheet.getByRole("textbox", { name: "Title", exact: true })).toBeVisible();

    await sheet.getByRole("tab", { name: "Event" }).click();
    sheet = page.getByLabel("New event");
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("tab", { name: "Event" })).toHaveAttribute("aria-selected", "true");
    await expect(sheet.getByRole("button", { name: "Create event", exact: true })).toBeVisible();
    await expect(sheet.getByRole("textbox", { name: "Title", exact: true })).toBeVisible();
    await expect(sheet.getByLabel("Course")).toHaveCount(0);
  });

  test("Calendar: date click opens a Task, slot drag and header button open an Event", async ({ page }) => {
    await page.goto("/calendar");
    await expect(page.getByRole("heading", { name: "Calendar" })).toBeVisible();
    await expect(page.locator(".fc-daygrid-day").first()).toBeVisible();

    // Header button defaults to the Event kind.
    await page.getByRole("button", { name: "+ New event", exact: true }).click();
    const eventSheet = page.getByLabel("New event");
    await expect(eventSheet).toBeVisible();
    await expect(eventSheet.getByRole("tab", { name: "Event" })).toHaveAttribute("aria-selected", "true");
    await eventSheet.getByRole("button", { name: "Close dialog" }).click();

    // A single date click opens a new Task with a due date.
    const days = page.locator(".fc-daygrid-day");
    await days.nth(5).click();
    const taskSheet = page.getByLabel("New task");
    await expect(taskSheet).toBeVisible();
    await expect(taskSheet.getByRole("tab", { name: "Task" })).toHaveAttribute("aria-selected", "true");
    await taskSheet.getByRole("button", { name: "Close dialog" }).click();

    // Dragging across days creates an Event (Calendar slot default).
    const boxA = await days.nth(8).boundingBox();
    const boxB = await days.nth(10).boundingBox();
    expect(boxA).not.toBeNull();
    expect(boxB).not.toBeNull();
    await page.mouse.move(boxA!.x + boxA!.width / 2, boxA!.y + boxA!.height / 2);
    await page.mouse.down();
    await page.mouse.move(boxB!.x + boxB!.width / 2, boxB!.y + boxB!.height / 2, { steps: 6 });
    await page.mouse.up();
    const dragged = page.getByLabel("New event");
    await expect(dragged).toBeVisible();
    await expect(dragged.getByRole("tab", { name: "Event" })).toHaveAttribute("aria-selected", "true");
  });

  test("tasks page initial load is small and duplicate-free", async ({ page }) => {
    const counts: Record<string, number> = {};
    page.on("request", (req) => {
      const url = new URL(req.url());
      if (!url.pathname.startsWith("/api/")) return;
      const key = `${req.method()} ${url.pathname}`;
      counts[key] = (counts[key] ?? 0) + 1;
    });
    await page.goto("/tasks");
    await expect(page.getByRole("heading", { name: "Tasks" })).toBeVisible();
    await expect(page.getByText(/\d+ items/)).toBeVisible();
    // Give any straggler requests time to land.
    await page.waitForTimeout(1200);
    const duplicates = Object.entries(counts).filter(([, n]) => n > 1);
    expect(duplicates, "duplicate GET/POST calls:\n" + JSON.stringify(counts, null, 2)).toEqual([]);
    expect(
      Object.keys(counts).length,
      "API calls per route:\n" + JSON.stringify(counts, null, 2)
    ).toBeLessThanOrEqual(8);
  });
});
