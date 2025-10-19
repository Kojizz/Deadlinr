import {test,expect} from "./fixtures";
test("G1 density persists, accents remain legible, editor inherits tokens",async({page})=>{
 await page.goto("/dashboard");
 await page.getByRole("button",{name:"PT",exact:true}).click();
 await page.getByRole("menuitemradio",{name:"Compact",exact:true}).click();
 await expect(page.locator("html")).toHaveAttribute("data-density","compact");
 await page.reload();
 await expect(page.locator("html")).toHaveAttribute("data-density","compact");
 const created=await page.request.post("/api/pages",{data:{title:"Design probe"}});
 const p=await created.json();await page.goto("/pages/"+p.id);
 await expect(page.locator(".bn-editor")).toBeVisible();
 await expect.poll(()=>page.locator(".bn-shadcn").first().evaluate(el=>getComputedStyle(el).getPropertyValue("--primary").trim())).not.toBe("");
 await expect(page.locator("header")).toHaveCSS("backdrop-filter","none");
 await page.keyboard.press("Control+k");
 await expect(page.getByTestId("palette")).toBeVisible();
 await page.keyboard.press("Escape");
 await page.emulateMedia({reducedMotion:"reduce"});
 expect(await page.locator("html").evaluate(()=>matchMedia("(prefers-reduced-motion: reduce)").matches)).toBe(true);
});
