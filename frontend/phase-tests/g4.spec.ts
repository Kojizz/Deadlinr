import {test,expect} from "./fixtures";
test("G4 inline counts, authenticated attachment download and bulk status",async({page})=>{
 for(const title of ["G4 one","G4 two"])expect((await page.request.post("/api/work-items",{data:{title,kind:"ASSIGNMENT",dueAt:new Date(Date.now()+3600000).toISOString()}})).ok()).toBeTruthy();
 await page.goto("/assignments");await page.getByLabel("Status of G4 one").selectOption("IN_PROGRESS");await expect(page.getByRole("button",{name:"In progress 1",exact:true})).toBeVisible();
 await page.getByRole("button",{name:"G4 one",exact:true}).click();await page.getByRole("tab",{name:"Files",exact:true}).click();await page.getByLabel("Attach files").setInputFiles({name:"acceptance.txt",mimeType:"text/plain",buffer:Buffer.from("G4 download proof")});await expect(page.getByText("acceptance.txt",{exact:true})).toBeVisible();
 const download=page.waitForEvent("download");await page.getByRole("button",{name:"Download",exact:true}).click();expect((await download).suggestedFilename()).toBe("acceptance.txt");await page.getByRole("button",{name:"Close dialog",exact:true}).click();
 await page.getByLabel("Select all visible").check();await page.getByLabel("Bulk status").selectOption("DONE");await expect(page.getByRole("button",{name:"Done 2",exact:true})).toBeVisible();
});
