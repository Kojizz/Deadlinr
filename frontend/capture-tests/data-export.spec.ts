import {test,expect,type Page} from "@playwright/test";
import {readFile} from "node:fs/promises";

async function settings(page:Page,{fail=false,refresh=false,countFail=false,empty=false,limit=false}={}){
 const writes:Record<string,unknown>[]=[],external:string[]=[];let failed=false,refreshed=0,countFailed=false;
 await page.route("**/*",async route=>{
  const url=new URL(route.request().url());if(!["localhost","127.0.0.1"].includes(url.hostname)){external.push(url.href);await route.abort();return;}
  if(!url.pathname.startsWith("/api/")){await route.continue();return;}
  const json=(body:unknown,status=200)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
  if(url.pathname==="/api/users/me")return json({id:1,fullName:"Demo Student",email:"demo@example.test",role:"STUDENT"});
  if(url.pathname==="/api/settings")return json({urgentDays:2,escalationCap:24,weeklyGoal:10,timeZone:"Asia/Qyzylorda",digestTime:"08:00",dailyDigest:false,weeklyReview:true,emailDigest:false});
  if(url.pathname==="/api/auth/refresh"){refreshed++;return json({});}
  if(url.pathname==="/api/data/summary"){
   if(countFail&&!countFailed)return json({message:"Temporary count failure"},503);
   return json({tasks:empty?0:limit?2001:2,documents:empty?0:url.searchParams.get("includeDocuments")==="true"?1:0,attachments:0,attachmentBytes:0,withinLimits:!limit,message:limit?"Selection exceeds the export limits.":"Export includes all matching records, not just the visible page."});
  }
  if(url.pathname==="/api/data/export"){
   writes.push(route.request().postDataJSON());
   if(!failed&&(fail||refresh)){failed=true;return json({message:fail?"Attachment unavailable. Retry or exclude attachments.":"Expired"},fail?409:401);}
   const format=writes.at(-1)?.format;
   if(format==="json")return json({format:"deadlinr",schemaVersion:1,tasks:[{title:"Report",dueAt:"2026-10-16T13:00:00Z",timeZone:"Asia/Qyzylorda"}]});
   return route.fulfill({contentType:format==="csv"?"text/csv":"text/markdown",body:format==="csv"?'title,dueAt,timeZone\r\n"Report","2026-10-16T13:00:00Z","Asia/Qyzylorda"\r\n':"- [ ] Report\n"});
  }
  if(url.pathname==="/api/workspace/summary")return json({totalPages:1,notes:1,tasks:2,events:0,openTasks:2,overdue:0,dueToday:0,dueThisWeek:0,createdThisWeek:0,trashCount:0,storageUsed:0});
  return json([]);
 });
 await page.goto("/settings#data");await expect(page.getByRole("heading",{name:"Data",exact:true})).toBeVisible();
 return {writes,external,refreshes:()=>refreshed,recoverCount:()=>{countFailed=true;}};
}

test("downloads every approved format with the selected scope and no external requests",async({page})=>{
 const {writes,external}=await settings(page);const data=page.getByRole("region",{name:"Data",exact:true});
 for(const format of ["json","csv","markdown"]){
  await data.getByLabel("Export format").selectOption(format);await expect(data.getByRole("button",{name:"Download export"})).toBeEnabled();
  if(format==="csv"){await expect(data.getByLabel("Include documents")).toHaveCount(0);await expect(data.getByLabel("Include attachment files")).toHaveCount(0);}
  const downloadPromise=page.waitForEvent("download");await data.getByRole("button",{name:"Download export"}).click();const download=await downloadPromise;
  expect(download.suggestedFilename()).toMatch(new RegExp(`^deadlinr-\\d{4}-\\d{2}-\\d{2}\\.${format==="markdown"?"md":format}$`));const body=await readFile((await download.path())!,"utf8");
  expect(body).toContain("Report");expect(writes.at(-1)).toEqual({format,includeDocuments:format!=="csv",includeAttachments:format==="json",includeArchived:false});
  await expect(data.getByRole("status")).toContainText("Download ready:");
 }
 expect(writes).toHaveLength(3);expect(external).toEqual([]);
});
test("a failed export retains choices and can be retried without attachments",async({page})=>{
 const {writes}=await settings(page,{fail:true});const data=page.getByRole("region",{name:"Data",exact:true});await data.getByLabel("Include trash").check();
 await data.getByRole("button",{name:"Download export"}).click();await expect(data.getByRole("alert")).toContainText("Attachment unavailable");await expect(data.getByLabel("Include trash")).toBeChecked();await expect(data.getByRole("status")).toBeEmpty();
 await data.getByLabel("Include attachment files").uncheck();const promise=page.waitForEvent("download");await data.getByRole("button",{name:"Download export"}).click();await promise;
 expect(writes.at(-1)).toMatchObject({includeAttachments:false,includeArchived:true});expect(writes).toHaveLength(2);
});
test("expired sessions refresh once before downloading",async({page})=>{
 const state=await settings(page,{refresh:true});const promise=page.waitForEvent("download");await page.getByRole("button",{name:"Download export"}).click();await promise;expect(state.refreshes()).toBe(1);expect(state.writes).toHaveLength(2);expect(state.writes[0]).toEqual(state.writes[1]);
});
test("count errors are recoverable, empty exports allowed and controls work by keyboard on narrow screens",async({page})=>{
 await page.setViewportSize({width:390,height:844});const state=await settings(page,{countFail:true,empty:true});const data=page.getByRole("region",{name:"Data",exact:true});
 await expect(data.getByRole("alert")).toContainText("Temporary count failure");await expect(data.getByRole("button",{name:"Download export"})).toBeDisabled();state.recoverCount();await data.getByRole("button",{name:"Retry count"}).click();await expect(data).toContainText("No tasks or documents");
 await data.getByLabel("Include trash").focus();await page.keyboard.press("Space");await expect(data.getByLabel("Include trash")).toBeChecked();await expect(data.getByRole("button",{name:"Download export"})).toBeEnabled();
 expect(await data.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);await data.scrollIntoViewIfNeeded();await page.screenshot({path:"test-results/data-export-mobile.png"});
});
test("oversized selections disable downloading",async({page})=>{
 const {writes}=await settings(page,{limit:true});await expect(page.getByRole("region",{name:"Data",exact:true})).toContainText("Selection exceeds");await expect(page.getByRole("button",{name:"Download export"})).toBeDisabled();expect(writes).toEqual([]);
});
