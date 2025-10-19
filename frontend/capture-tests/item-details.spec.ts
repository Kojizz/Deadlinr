import {test,expect,type Page} from "@playwright/test";
import type {WorkItem} from "../src/lib/work";

async function details(page:Page,{long=false,filesFail=false}={}){
 const item:WorkItem={id:1,ownerId:1,kind:"TASK",title:"Research report",description:long?"Notes for the report.\n".repeat(100):"Compare the sources and write the final report.",pageId:10,pageTitle:"Research notes",courseId:null,parentId:null,status:"IN_PROGRESS",priority:2,dueAt:"2026-10-16T13:00:00.000Z",startAt:"2026-10-15T04:00:00.000Z",allDay:false,hardDeadline:true,location:"",link:"",repeatRule:"WEEKLY",labels:[long?"x".repeat(100):"university"],reminderMinutes:[60],position:0,completedAt:null,archivedAt:null,createdAt:"2026-10-12T05:00:00.000Z",version:4,timeZone:"Asia/Almaty",fileCount:1,subtaskCount:1,subtasksDone:0,blocked:false};
 const child={...item,id:2,title:"Review sources",parentId:1,subtaskCount:0,fileCount:0};
 const writes:Record<string,unknown>[]=[],external:string[]=[],reads:string[]=[];
 let fail=filesFail;
 const fileName=long?"long-"+"name".repeat(60)+".pdf":"Report rubric.pdf";
 await page.clock.setFixedTime(new Date("2026-10-13T05:00:00Z"));
 await page.route("**/*",async route=>{
  const url=new URL(route.request().url());if(!["localhost","127.0.0.1"].includes(url.hostname)){external.push(url.href);return route.abort();}
  if(!url.pathname.startsWith("/api/"))return route.continue();
  const method=route.request().method();const json=(body:unknown,status=200)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
  if(method==="GET")reads.push(url.pathname+url.search);
  else writes.push({path:url.pathname,method,...route.request().postDataJSON()});
  if(url.pathname==="/api/users/me")return json({id:1,fullName:"Demo Student",email:"demo@example.test",role:"STUDENT"});
  if(url.pathname==="/api/work-items/1"&&method==="PATCH"){Object.assign(item,route.request().postDataJSON());return json(item);}
  if(url.pathname==="/api/work-items/1")return json(item);
  if(url.pathname==="/api/work-items/1/files")return fail?json({message:"File service unavailable"},503):json([{id:"rubric",name:fileName,size:2048,contentType:"application/pdf",uploadedAt:item.createdAt}]);
  if(url.pathname==="/api/files/rubric")return route.fulfill({contentType:"application/pdf",body:"Test attachment"});
  if(url.pathname==="/api/work-items")return json({items:url.searchParams.has("parentId")?[child]:[item],total:1,counts:{all:1,todo:0,progress:1,done:0,overdue:0},groupCounts:[],priorityCounts:{},page:0,size:100});
  if(url.pathname==="/api/workspace/summary")return json({totalPages:1,notes:1,tasks:2,events:0,openTasks:2,overdue:0,dueToday:0,dueThisWeek:0,createdThisWeek:0,trashCount:0,storageUsed:2048});
  if(url.pathname==="/api/history")return json({canUndo:false,canRedo:false});
  if(url.pathname==="/api/workspace/pages")return json([{id:10,title:"Research notes"}]);
  return json([]);
 });
 await page.goto("/tasks?task=1");const dialog=page.getByRole("dialog",{name:"Item details",exact:true});
 await expect(dialog.getByRole("heading",{name:item.title,exact:true})).toBeVisible();
 return {item,dialog,writes,reads,external,fileName,recover:()=>{fail=false;}};
}

test("details fit their content, preserve saved dates, and load subtasks only when expanded",async({page})=>{
 await page.setViewportSize({width:1440,height:1000});const state=await details(page);
 const {dialog,item}=state;await expect(dialog.getByRole("button",{name:"Download Report rubric.pdf"})).toBeVisible();
 await expect(dialog.getByLabel("Item properties")).toContainText("18:00 · UTC+5");
 await expect(dialog.getByLabel("Item properties")).toContainText("09:00 · UTC+5");
 await expect(dialog.locator("time").first()).toHaveAttribute("datetime",item.dueAt!);
 await expect(dialog.getByRole("link",{name:/Linked page Research notes/})).toHaveAttribute("href","/pages/10");
 expect(await dialog.boundingBox()).toMatchObject({width:480});expect((await dialog.boundingBox())!.height).toBeLessThan(850);
 await expect(dialog.getByText("Created",{exact:true})).not.toBeVisible();
 expect(state.reads.some(path=>path.includes("parentId="))).toBe(false);
 await dialog.locator("summary").filter({hasText:"Subtasks"}).click();await expect(dialog.getByRole("button",{name:"Review sources",exact:true})).toBeVisible();
 await dialog.locator("summary").filter({hasText:"Activity"}).click();await expect(dialog.getByText("Created",{exact:true})).toBeVisible();
 const download=page.waitForEvent("download");await dialog.getByRole("button",{name:"Download Report rubric.pdf"}).click();expect((await download).suggestedFilename()).toBe("Report rubric.pdf");
 await page.keyboard.press("Escape");await expect(dialog).not.toBeVisible();expect(state.writes).toEqual([]);expect(state.external).toEqual([]);
});

test("edit sections and Ctrl+Enter saving remain available without changing UTC instants",async({page})=>{
 const state=await details(page);await state.dialog.getByRole("button",{name:"Edit",exact:true}).click();
 const editor=page.getByRole("dialog",{name:"Edit item",exact:true});await expect(editor.getByLabel("Title",{exact:true})).toHaveValue("Research report");
 for(const section of ["Subtasks","Files","Reminders","Planning","Details"])await expect(editor.getByRole("tab",{name:section,exact:true})).toBeVisible();
 await editor.getByLabel("Title",{exact:true}).fill("Updated report");await editor.getByLabel("Title",{exact:true}).press("Control+Enter");
 await expect(editor).not.toBeVisible();expect(state.writes).toHaveLength(1);expect(state.writes[0]).toMatchObject({method:"PATCH",title:"Updated report",dueAt:"2026-10-16T13:00:00.000Z",startAt:"2026-10-15T04:00:00.000Z",timeZone:"Asia/Almaty",version:4});
});

test("attachment failures show a retry and downloads still work after recovery",async({page})=>{
 const state=await details(page,{filesFail:true});await expect(state.dialog.getByRole("alert")).toHaveText("Could not load files.");state.recover();
 await state.dialog.getByRole("button",{name:"Retry files"}).click();await expect(state.dialog.getByRole("button",{name:"Download Report rubric.pdf"})).toBeVisible();expect(state.writes).toEqual([]);
});

for(const theme of ["light","dark"]){
 test(`mobile ${theme} details scroll vertically without hiding footer or overflowing`,async({page})=>{
  await page.addInitScript(theme=>localStorage.setItem("theme",theme),theme);
  await page.setViewportSize({width:390,height:844});const {dialog,fileName}=await details(page,{long:true});
  await expect(dialog.getByRole("button",{name:"Edit",exact:true})).toBeInViewport();await expect(dialog.getByRole("button",{name:"Close",exact:true})).toBeInViewport();
  expect(await dialog.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
  expect(await dialog.locator(".item-detail-body").evaluate(el=>el.scrollHeight>el.clientHeight&&el.scrollWidth<=el.clientWidth)).toBe(true);
  await dialog.getByRole("button",{name:"Download "+fileName}).scrollIntoViewIfNeeded();await expect(dialog.getByRole("button",{name:"Edit",exact:true})).toBeInViewport();
  expect((await dialog.getByRole("button",{name:"Download "+fileName}).boundingBox())!.height).toBeGreaterThanOrEqual(44);
  await page.screenshot({path:`test-results/item-details-mobile-${theme}.png`});
 });
}
