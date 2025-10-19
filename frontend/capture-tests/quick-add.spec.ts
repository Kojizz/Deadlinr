import {test,expect,type Page} from "@playwright/test";
import type {WorkItem} from "../src/lib/work";

async function workspace(page:Page,{ai=false,failChild=false,failSave=false}={}){
 const items:WorkItem[]=[];const writes:Record<string,unknown>[]=[];const external:string[]=[];
 let rejected=false;
 await page.clock.setFixedTime(new Date("2026-10-12T05:00:00Z"));
 await page.route("**/*",async route=>{
  const url=new URL(route.request().url());
  if(!["localhost","127.0.0.1"].includes(url.hostname)){external.push(url.href);await route.abort();return;}
  if(!url.pathname.startsWith("/api/")){await route.continue();return;}
  const json=(body:unknown,status=200)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
  if(url.pathname==="/api/users/me")return json({id:1,fullName:"Demo Student",email:"demo@example.test",role:"STUDENT"});
  if(url.pathname==="/api/local-ai")return json(route.request().method()==="POST"?{subtasks:["Read rubric","Write outline","Write draft"]}:ai?{available:true,model:"local:latest"}:{available:false,message:"Enable local AI: run Ollama with a downloaded local model. Task capture works without it."});
  if(url.pathname==="/api/work-items"&&route.request().method()==="POST"){
   const input=route.request().postDataJSON();writes.push(input);
   if(!rejected&&(failSave||(failChild&&input.title==="Write outline"))){rejected=true;return json({message:"Temporary save failure"},503);}
   const item={id:items.length+1,ownerId:1,kind:"TASK",description:"",status:"TODO",priority:3,labels:[],reminderMinutes:[],dueAt:null,startAt:null,allDay:false,timeZone:"Asia/Qyzylorda",subtaskCount:0,subtasksDone:0,fileCount:0,version:0,...input};
   items.push(item);return json(item,201);
  }
  if(url.pathname==="/api/work-items")return json({items,total:items.length,counts:{all:items.length,todo:items.length,progress:0,done:0,overdue:0},groupCounts:[],priorityCounts:{},page:0,size:100});
  if(/^\/api\/work-items\/\d+$/.test(url.pathname))return json(items.find(w=>w.id===Number(url.pathname.split("/").at(-1))));
  if(url.pathname==="/api/workspace/summary")return json({totalPages:0,notes:0,tasks:items.length,events:0,openTasks:items.length,overdue:0,dueToday:0,dueThisWeek:0,createdThisWeek:0,trashCount:0,storageUsed:0});
  if(url.pathname==="/api/history")return json({canUndo:false,canRedo:false});
  return json([]);
 });
 await page.goto("/tasks");await expect(page.getByRole("button",{name:"Quick add",exact:true})).toBeVisible();
 return {items,writes,external};
}

test("preview is editable, saves once on confirmation, and reload preserves the deadline",async({page})=>{
 const {items,writes,external}=await workspace(page);
 await page.keyboard.press("Control+k");const dialog=page.getByRole("dialog",{name:"Quick add",exact:true});
 await page.getByLabel("Quick add text").fill("Report due Friday 18:00 #work p1");
 await expect(page.getByLabel("Preview title")).toHaveValue("Report");
 await expect(page.getByLabel("Preview deadline")).toHaveValue("2026-10-16T18:00");
 await expect(page.getByLabel("Preview priority")).toHaveValue("1");
 await page.getByLabel("Quick add text").press("Enter");expect(writes).toHaveLength(0);
 await page.getByLabel("Preview title").fill("Final report");
 await page.getByLabel("Preview deadline").fill("2026-10-16T19:30");
 await page.getByLabel("Preview start time").fill("2026-10-15T09:00");
 await page.getByLabel("Preview labels").fill("work, uni");
 await page.getByLabel("Preview priority").selectOption("2");
 await dialog.getByRole("button",{name:"Add task",exact:true}).click();
 await expect(dialog).not.toBeVisible();expect(writes).toHaveLength(1);
 expect(items[0]).toMatchObject({title:"Final report",dueAt:"2026-10-16T14:30:00.000Z",startAt:"2026-10-15T04:00:00.000Z",timeZone:"Asia/Qyzylorda",labels:["work","uni"],priority:2});
 await page.reload();await page.getByRole("button",{name:"Final report",exact:true}).click();
 const details=page.getByRole("dialog",{name:"Item details",exact:true});
 await expect(details).toContainText("19:30");await expect(details).toContainText("09:00");await expect(details).toContainText("UTC+5");
 expect(external).toEqual([]);
});

test("q and both command shortcuts open capture; search remains accessible and focus returns",async({page})=>{
 await workspace(page);
 const trigger=page.getByRole("button",{name:"Quick add",exact:true});
 for(const key of ["q","Control+k","Meta+k"]){
  await trigger.focus();await page.keyboard.press(key);const dialog=page.getByRole("dialog",{name:"Quick add",exact:true});
  await expect(dialog).toBeVisible();await page.getByLabel("Quick add text").fill("Buy paper");await expect(page.getByLabel("Preview title")).toHaveValue("Buy paper");
  for(let i=0;i<14;i++){await page.keyboard.press("Tab");expect(await dialog.evaluate(el=>el.contains(document.activeElement))).toBe(true);}
  await page.keyboard.press("Escape");await expect(dialog).not.toBeVisible();await expect(trigger).toBeFocused();
 }
 await page.getByLabel("Search list").focus();await page.keyboard.press("q");await expect(page.getByRole("dialog",{name:"Quick add",exact:true})).not.toBeVisible();
 await page.keyboard.press("Control+Shift+k");await expect(page.getByTestId("palette")).toBeVisible();await page.keyboard.press("Escape");
});

test("no local model, cancel and failed saves never lose the draft",async({page})=>{
 const {items,writes}=await workspace(page,{failSave:true});
 await page.getByRole("button",{name:"Quick add",exact:true}).click();
 await page.getByLabel("Quick add text").fill("Essay tomorrow");await expect(page.getByLabel("Preview deadline")).toHaveValue("2026-10-13");
 await expect(page.getByText(/Enable local AI:/)).toBeVisible();await expect(page.getByRole("button",{name:"Break this task into subtasks"})).toHaveCount(0);
 await page.getByRole("button",{name:"Add task",exact:true}).click();await expect(page.getByRole("dialog",{name:"Quick add",exact:true}).getByRole("alert")).toContainText("Temporary save failure");
 await expect(page.getByLabel("Preview title")).toHaveValue("Essay");expect(items).toHaveLength(0);
 await page.getByRole("button",{name:"Add task",exact:true}).click();await expect(page.getByRole("dialog",{name:"Quick add",exact:true})).not.toBeVisible();
 expect(items).toHaveLength(1);expect(items[0].dueAt).toBe("2026-10-13T18:59:00.000Z");
 await page.getByRole("button",{name:"Quick add",exact:true}).click();await page.getByLabel("Quick add text").fill("Cancel me Friday");await page.getByRole("button",{name:"Cancel",exact:true}).click();expect(writes).toHaveLength(2);
});

test("local AI suggestions are editable and partial retries do not duplicate saved items",async({page})=>{
 const {items,writes}=await workspace(page,{ai:true,failChild:true});
 await page.keyboard.press("q");await page.getByLabel("Quick add text").fill("Essay tomorrow");
 await page.getByRole("button",{name:"Break this task into subtasks"}).click();
 await page.getByLabel("Subtask 1 title").fill("Review rubric");await page.getByLabel("Include subtask 3").uncheck();expect(writes).toHaveLength(0);
 await page.getByRole("button",{name:"Add task",exact:true}).click();await expect(page.getByRole("dialog",{name:"Quick add",exact:true}).getByRole("alert")).toContainText("Your task is saved");
 await page.getByRole("button",{name:"Retry remaining subtasks"}).click();await expect(page.getByRole("dialog",{name:"Quick add",exact:true})).not.toBeVisible();
 expect(items.map(w=>w.title)).toEqual(["Essay","Review rubric","Write outline"]);
 expect(items.slice(1).every(w=>w.parentId===items[0].id)).toBe(true);
 expect(writes.filter(w=>w.title==="Essay")).toHaveLength(1);expect(writes.filter(w=>w.title==="Review rubric")).toHaveLength(1);
});

test("a new phrase cannot save stale parsed content and invalid start times block saving",async({page})=>{
 const {writes}=await workspace(page);await page.keyboard.press("q");
 await page.getByLabel("Quick add text").fill("Old task tomorrow");await expect(page.getByLabel("Preview title")).toHaveValue("Old task");
 await page.getByLabel("Quick add text").fill("New task Friday 18:00");await expect(page.getByRole("button",{name:"Add task",exact:true})).toBeDisabled();
 await expect(page.getByLabel("Preview title")).toHaveValue("New task");await page.getByLabel("Preview start time").fill("2026-10-17T09:00");
 await expect(page.getByRole("dialog",{name:"Quick add",exact:true}).getByRole("alert")).toContainText("Start time must be before");await expect(page.getByRole("button",{name:"Add task",exact:true})).toBeDisabled();expect(writes).toHaveLength(0);
});

test("capture fits a narrow viewport",async({page})=>{
 await page.setViewportSize({width:390,height:844});await workspace(page);await page.getByRole("button",{name:"Quick add",exact:true}).click();
 await page.getByLabel("Quick add text").fill("Essay tomorrow");await expect(page.getByLabel("Preview title")).toHaveValue("Essay");
 const dialog=page.getByRole("dialog",{name:"Quick add",exact:true});
 expect(await dialog.evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
 await page.screenshot({path:"test-results/capture-mobile.png"});
});
test("saved deadline displays in its original zone on another machine",async({browser})=>{
 const context=await browser.newContext({timezoneId:"America/New_York"});const page=await context.newPage();
 try{
  const {items}=await workspace(page);
  items.push({id:1,title:"Travel report",kind:"TASK",description:"",status:"TODO",priority:2,labels:[],reminderMinutes:[],dueAt:"2026-10-16T14:30:00.000Z",startAt:null,allDay:false,timeZone:"Asia/Qyzylorda",subtaskCount:0,subtasksDone:0,fileCount:0} as unknown as WorkItem);
  await page.reload();await expect(page.getByRole("article",{name:"Travel report"})).toContainText("19:30");
  await page.getByRole("button",{name:"Travel report",exact:true}).click();
  await expect(page.getByRole("dialog",{name:"Item details",exact:true})).toContainText("19:30");
 }finally{await context.close();}
});

for(const timeZone of ["Asia/Almaty","Asia/Qyzylorda"]){
 test(`timezone labels describe clock offset, not a city: ${timeZone}`,async({browser})=>{
  const context=await browser.newContext({timezoneId:timeZone});const page=await context.newPage();
  try{
   const {items,writes}=await workspace(page);await page.keyboard.press("Control+k");
   await page.getByLabel("Quick add text").fill("Offset check due Friday 18:00");await expect(page.getByLabel("Preview deadline")).toHaveValue("2026-10-16T18:00");
   await expect(page.getByLabel("Capture timezone")).toHaveText("Timezone: UTC+5");
   await page.getByRole("button",{name:"Add task",exact:true}).click();
   expect(writes[0]).toMatchObject({dueAt:"2026-10-16T13:00:00.000Z",timeZone});
   await page.reload();await page.getByRole("button",{name:"Offset check",exact:true}).click();
   const details=page.getByRole("dialog",{name:"Item details",exact:true});await expect(details).toContainText("18:00 · UTC+5");await expect(details).not.toContainText(timeZone);
   expect(items[0].dueAt).toBe("2026-10-16T13:00:00.000Z");expect(writes).toHaveLength(1);
  }finally{await context.close();}
 });
}
