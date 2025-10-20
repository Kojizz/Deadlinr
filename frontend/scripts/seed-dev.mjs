#!/usr/bin/env node
/**
 * DEV-ONLY seed script — fills a FRESH throwaway account with realistic data
 * (~300 tasks, ~100 pages, ~30 events) so perf measurements run against a
 * realistic dataset. Never run against a shared or production backend: it
 * creates real rows. Not part of any production seed/migration path.
 *
 * Usage: node scripts/seed-dev.mjs [--base http://localhost:8080/api]
 *        [--tasks 300] [--pages 100] [--events 30]
 * Prints the seeded account email at the end (password: secret123).
 */
const args = Object.fromEntries(process.argv.slice(2).map((a, i, all) => a.startsWith("--") ? [a.slice(2), all[i + 1] && !all[i + 1].startsWith("--") ? all[i + 1] : "true"] : null).filter(Boolean));
const BASE = (args.base ?? "http://localhost:8080/api").replace(/\/$/, "");
const N_TASKS = Number(args.tasks ?? 300), N_PAGES = Number(args.pages ?? 100), N_EVENTS = Number(args.events ?? 30);
const ZONE = "Asia/Almaty";

const jar = new Map();
function cookieHeader() { return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join("; "); }
async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "content-type": "application/json", cookie: cookieHeader() },
    body: body == null ? undefined : JSON.stringify(body),
  });
  for (const c of res.headers.getSetCookie?.() ?? []) {
    const [pair] = c.split(";"); const eq = pair.indexOf("=");
    if (eq > 0) jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${(await res.text()).slice(0, 200)}`);
  return res.status === 204 ? null : res.json();
}
async function batch(items, size, fn) {
  const out = [];
  for (let i = 0; i < items.length; i += size) out.push(...await Promise.all(items.slice(i, i + size).map(fn)));
  return out;
}
const rnd = (n) => Math.floor(Math.random() * n);
const pick = (arr) => arr[rnd(arr.length)];

const email = `seed-${Date.now()}@deadlinr.local`;
console.log(`Seeding into ${BASE} as ${email} …`);
await call("POST", "/auth/register", { fullName: "Seed Data", email, password: "secret123" });

// ~100 pages: 20 roots, the rest nested one or two levels deep.
const roots = await batch(Array.from({ length: 20 }, (_, i) => `Area ${i + 1}`), 8,
  (title) => call("POST", "/pages", { title, content: null }));
const midPages = await batch(Array.from({ length: 40 }, (_, i) => `Project ${i + 1}`), 8,
  (title) => call("POST", "/pages", { title, parentId: pick(roots).id, content: null }));
const leafPages = await batch(Array.from({ length: N_PAGES - 60 }, (_, i) => `Note ${i + 1}`), 8,
  (title) => call("POST", "/pages", { title, parentId: pick(midPages).id, content: null }));
const pages = [...roots, ...midPages, ...leafPages];
console.log(`pages: ${pages.length}`);

const LABELS = ["ml", "university", "work", "reading", "admin", "health", "dev", "exam"];
const VERBS = ["Write", "Review", "Submit", "Prepare", "Fix", "Read", "Plan", "Practice", "Draft", "Study"];
const TOPICS = ["lab report", "chapter notes", "slides", "essay draft", "problem set", "flashcards", "retrospective", "literature review", "quiz prep", "project milestone"];
const lorem = "Context, links and notes for this item. ".repeat(1 + rnd(12)).trim();

function taskBody(i) {
  const roll = Math.random();
  const due = roll < 0.15 ? Date.now() - (1 + rnd(6)) * 864e5      // overdue
    : roll < 0.30 ? Date.now() + rnd(2) * 36e5                     // today
    : roll < 0.65 ? Date.now() + (1 + rnd(7)) * 864e5              // this week
    : roll < 0.85 ? Date.now() + (8 + rnd(20)) * 864e5             // later
    : null;                                                        // no date
  return {
    kind: "TASK",
    title: `${pick(VERBS)} ${pick(TOPICS)} ${i + 1}`.slice(0, 120),
    description: Math.random() < 0.75 ? lorem : "",
    status: Math.random() < 0.12 ? "DONE" : Math.random() < 0.3 ? "IN_PROGRESS" : "TODO",
    priority: pick([1, 2, 2, 3, 3, 3, 4]),
    dueAt: due == null ? null : new Date(due).toISOString(),
    allDay: Math.random() < 0.4,
    hardDeadline: Math.random() < 0.06 && due != null,
    labels: [...new Set(Array.from({ length: rnd(3) }, () => pick(LABELS)))],
    reminderMinutes: Math.random() < 0.5 ? [60] : [],
    repeatRule: Math.random() < 0.08 ? pick(["DAILY", "WEEKLY", "MONTHLY"]) : "NONE",
    pageId: Math.random() < 0.35 ? pick(pages).id : null,
    timeZone: ZONE,
  };
}
const tasks = await batch(Array.from({ length: N_TASKS }, (_, i) => i), 8,
  (i) => call("POST", "/work-items", taskBody(i)));
console.log(`tasks: ${tasks.length}`);

// Subtasks: give ~15% of tasks 1–3 children.
const withKids = tasks.filter(() => Math.random() < 0.15);
let subCount = 0;
await batch(withKids, 8, async (parent) => {
  for (let k = 0; k < 1 + rnd(3); k++) {
    await call("POST", "/work-items", { kind: "TASK", title: `Step ${k + 1} of ${parent.title}`.slice(0, 120), parentId: parent.id, status: Math.random() < 0.4 ? "DONE" : "TODO", priority: parent.priority, timeZone: ZONE });
    subCount++;
  }
});
console.log(`subtasks: ${subCount}`);

const events = await batch(Array.from({ length: N_EVENTS }, (_, i) => i), 8,
  (i) => {
    const start = Date.now() + (rnd(20) - 5) * 864e5 + rnd(8) * 36e5;
    return call("POST", "/work-items", {
      kind: "EVENT", title: `${pick(["Lecture", "Seminar", "Meeting", "Office hours", "Exam", "Study group"])} ${i + 1}`,
      startAt: new Date(start).toISOString(), dueAt: new Date(start + (1 + rnd(3)) * 36e5).toISOString(),
      allDay: false, priority: 3, labels: [pick(LABELS)], reminderMinutes: [60], timeZone: ZONE,
    });
  });
console.log(`events: ${events.length}`);
console.log(`Done. Log in as ${email} / secret123 to measure with this dataset.`);
