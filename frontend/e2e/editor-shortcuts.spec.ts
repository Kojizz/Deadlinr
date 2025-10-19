import {test,expect,type Page} from "@playwright/test";

async function documentWith(page:Page,type:string){
 const response=await page.request.post("/api/pages",{data:{title:"Shortcut check",content:[{type,content:[{type:"text",text:"Keep this text",styles:{}}]}]}});expect(response.ok()).toBeTruthy();const record=await response.json();await page.goto(`/pages/${record.id}`);await page.locator(`[data-content-type="${type}"]`).filter({hasText:"Keep this text"}).click();return record.id as number;
}
test("Ctrl+Alt+C formats text as a code block and saves it",async({page})=>{
 const id=await documentWith(page,"paragraph");await page.keyboard.press("Control+Alt+c");
 await expect(page.locator('[data-content-type="codeBlock"]')).toContainText("Keep this text");
 await expect.poll(async()=>{const record=await(await page.request.get(`/api/pages/${id}`)).json();return record.content[0].type;}).toBe("codeBlock");await page.reload();await expect(page.locator('[data-content-type="codeBlock"]')).toContainText("Keep this text");
});
test("Ctrl+Alt+0 uses the physical digit key across keyboard layouts",async({page})=>{
 const id=await documentWith(page,"heading");await page.locator(".bn-editor").dispatchEvent("keydown",{key:"à",code:"Digit0",ctrlKey:true,altKey:true,bubbles:true,cancelable:true});
 await expect(page.locator('[data-content-type="heading"]')).toHaveCount(0);await expect(page.locator('[data-content-type="paragraph"]').filter({hasText:"Keep this text"})).toBeVisible();
 await expect.poll(async()=>{const record=await(await page.request.get(`/api/pages/${id}`)).json();return record.content[0].type;}).toBe("paragraph");await page.reload();await expect(page.locator('[data-content-type="paragraph"]').filter({hasText:"Keep this text"})).toBeVisible();
});
test("physical code shortcut supports changed characters and the Mac command modifier",async({page})=>{
 await documentWith(page,"paragraph");const editor=page.locator(".bn-editor");
 await editor.dispatchEvent("keydown",{key:"ç",code:"KeyC",ctrlKey:true,altKey:true,bubbles:true,cancelable:true});await expect(page.locator('[data-content-type="codeBlock"]')).toContainText("Keep this text");
 await page.keyboard.press("Control+Alt+0");await expect(page.locator('[data-content-type="paragraph"]').filter({hasText:"Keep this text"})).toBeVisible();
 await editor.dispatchEvent("keydown",{key:"ç",code:"KeyC",metaKey:true,altKey:true,bubbles:true,cancelable:true});await expect(page.locator('[data-content-type="codeBlock"]')).toContainText("Keep this text");
 await editor.dispatchEvent("keydown",{key:"º",code:"Digit0",metaKey:true,altKey:true,bubbles:true,cancelable:true});await expect(page.locator('[data-content-type="paragraph"]').filter({hasText:"Keep this text"})).toBeVisible();
});
test("formatting shortcuts respect title fields, AltGr, composition and open dialogs",async({page})=>{
 await documentWith(page,"heading");await page.getByRole("textbox",{name:"Page title",exact:true}).focus();await page.keyboard.press("Control+Alt+c");await page.keyboard.press("Control+Alt+0");await expect(page.locator('[data-content-type="heading"]')).toContainText("Keep this text");
 const editor=page.locator(".bn-editor");await page.locator('[data-content-type="heading"]').click();
 for(const guard of [{modifierAltGraph:true},{isComposing:true},{repeat:true}]){
  for(const [code,key] of [["KeyC","c"],["Digit0","0"]])await editor.dispatchEvent("keydown",{code,key,ctrlKey:true,altKey:true,bubbles:true,cancelable:true,...guard});
  await expect(page.locator('[data-content-type="heading"]')).toContainText("Keep this text");
 }
 await page.getByRole("button",{name:"Keyboard shortcuts",exact:true}).click();await expect(page.getByText("Code block in document body",{exact:true})).toBeVisible();await expect(page.getByText("Paragraph in document body",{exact:true})).toBeVisible();
 await editor.dispatchEvent("keydown",{key:"0",code:"Digit0",ctrlKey:true,altKey:true,bubbles:true,cancelable:true});await expect(page.locator('[data-content-type="heading"]')).toContainText("Keep this text");
});
