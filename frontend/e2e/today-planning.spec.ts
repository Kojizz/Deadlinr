import {test,expect,type Page,type APIResponse} from "@playwright/test";

async function account(page:Page){
 const response=await page.request.post("/api/auth/register",{data:{fullName:"Today Student",email:`today-${Date.now()}-${Math.random()}@example.test`,password:"Today-demo-123!"}});expect(response.ok()).toBeTruthy();
}
async function task(page:Page,fields:Record<string,unknown>){
 const response=await page.request.post("/api/work-items",{data:{kind:"TASK",reminderMinutes:[],timeZone:"Asia/Almaty",...fields}});expect(response.ok()).toBeTruthy();return response.json();
}
async function offline(page:Page){
 const external:string[]=[];await page.route("**/*",async route=>{if(!["localhost","127.0.0.1"].includes(new URL(route.request().url()).hostname)){external.push(route.request().url());return route.abort();}return route.continue();});return external;
}
test("Today explains blockers and keeps planned work separate from its deadline on reload",async({page})=>{
 const external=await offline(page);await account(page);const now=Date.now();
 const blocker=await task(page,{title:"Read the rubric"});
 const overdue=await task(page,{title:"Submit coursework",dueAt:new Date(now-3600000).toISOString(),hardDeadline:true});
 const relation=await page.request.post(`/api/work-items/${overdue.id}/dependencies`,{data:{dependsOnId:blocker.id}});expect(relation.ok()).toBeTruthy();
 const planned=await task(page,{title:"Write the report",startAt:new Date(now).toISOString(),dueAt:new Date(now+5*86400000).toISOString()});
 await task(page,{title:"Completed work",status:"DONE",dueAt:new Date(now-3600000).toISOString()});
 await task(page,{title:"Lecture",kind:"EVENT",dueAt:new Date(now-3600000).toISOString()});
 await page.goto("/today");await expect(page.getByRole("heading",{name:"Today",exact:true})).toBeVisible();
 const blocked=page.getByRole("article",{name:"Submit coursework",exact:true});await expect(blocked).toContainText("The deadline has passed");await expect(blocked).toContainText("unfinished dependency");await expect(blocked.getByRole("button",{name:"Complete task"})).toBeDisabled();
 await blocked.getByRole("link",{name:"Review dependencies"}).click();const editor=page.getByRole("dialog",{name:"Edit item",exact:true});await expect(editor.getByRole("tab",{name:"Planning",exact:true})).toHaveAttribute("aria-selected","true");await expect(editor).toContainText(blocker.title);await page.keyboard.press("Escape");await page.goto("/today");
 const report=page.getByRole("article",{name:"Write the report",exact:true});await expect(report).toContainText("Planned start · today");await expect(report.getByRole("term").filter({hasText:"Deadline"})).toBeVisible();
 await expect(page.getByRole("article",{name:"Completed work",exact:true})).toHaveCount(0);await expect(page.getByRole("article",{name:"Lecture",exact:true})).toHaveCount(0);
 await page.reload();await expect(report).toBeVisible();
 const stored=await(await page.request.get(`/api/work-items/${planned.id}`)).json();expect(stored.dueAt).toBe(planned.dueAt);expect(stored.startAt).toBe(planned.startAt);expect(stored.version).toBe(planned.version);
 await report.getByRole("button",{name:"Start task"}).focus();await page.keyboard.press("Enter");await expect(report).toContainText("In progress");
 expect((await(await page.request.get(`/api/work-items/${planned.id}`)).json()).dueAt).toBe(planned.dueAt);expect(external).toEqual([]);
});
test("completion refreshes the plan and a stale update preserves the task",async({page})=>{
 await account(page);const first=await task(page,{title:"Finish the outline",status:"IN_PROGRESS"});
 await page.goto("/today");const card=page.getByRole("article",{name:first.title,exact:true});await expect(card).toBeVisible();
 const updated=await page.request.patch(`/api/work-items/${first.id}`,{data:{priority:1,version:first.version}});expect(updated.ok()).toBeTruthy();
 await card.getByRole("button",{name:"Complete task"}).click();await expect(page.getByRole("status").filter({hasText:/changed|reload|conflict/i})).toBeVisible();
 await expect(card).toBeVisible();expect((await(await page.request.get(`/api/work-items/${first.id}`)).json()).status).toBe("IN_PROGRESS");
 await card.getByRole("button",{name:"Complete task"}).click();await expect(card).toHaveCount(0);await expect(page.getByText("A clear day",{exact:true})).toBeVisible();
 await page.reload();await expect(page.getByText("A clear day",{exact:true})).toBeVisible();
});
test("upcoming deadlines paginate and show a recoverable read failure",async({page})=>{
 await account(page);const due=new Date(Date.now()+2*86400000).toISOString();
 for(let i=0;i<21;i++)await task(page,{title:`Upcoming assignment ${i}`,dueAt:due});
 await page.goto("/today");await expect(page.getByText("A clear day",{exact:true})).toBeVisible();
 await page.getByRole("button",{name:"Next seven days"}).click();await expect(page.getByRole("article")).toHaveCount(20);await expect(page.getByText("Page 1 of 2")).toBeVisible();
 await page.getByRole("navigation",{name:"Plan pagination"}).getByRole("button",{name:"Next",exact:true}).click();await expect(page.getByRole("article")).toHaveCount(1);
 await page.getByRole("article").getByRole("button",{name:"Complete task"}).click();await expect(page.getByRole("article")).toHaveCount(20);await expect(page.getByRole("navigation",{name:"Plan pagination"})).toHaveCount(0);
 await page.route("**/api/planning/today?**",route=>route.fulfill({status:503,contentType:"application/json",body:JSON.stringify({message:"Temporary failure"})}));
 await page.reload();await expect(page.getByRole("alert").filter({hasText:"Could not load your plan"})).toBeVisible();
 await page.unroute("**/api/planning/today?**");await page.getByRole("alert").getByRole("button",{name:"Retry"}).click();await expect(page.getByRole("article")).toHaveCount(0);await expect(page.getByText("A clear day",{exact:true})).toBeVisible();
});
test("Today fits a narrow screen and the dashboard points to it",async({page})=>{
 await account(page);await task(page,{title:"Prepare presentation",status:"IN_PROGRESS"});
 await page.setViewportSize({width:390,height:844});await page.goto("/dashboard");await page.getByRole("link",{name:"Plan today",exact:true}).click();await expect(page).toHaveURL(/\/today$/);
 await expect(page.getByRole("article",{name:"Prepare presentation"})).toBeVisible();
 expect(await page.locator("main").evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
 await page.screenshot({path:"test-results/today-mobile.png"});
});
test("deep page navigation uses shared metadata and keeps all breadcrumbs",async({page})=>{
 await account(page);let parentId:number|null=null;const ids:number[]=[];
 for(let i=0;i<10;i++){const response:APIResponse=await page.request.post("/api/pages",{data:{title:`Ancestor ${i}`,parentId}});expect(response.ok()).toBeTruthy();parentId=(await response.json()).id;ids.push(parentId!);}
 const ancestors:string[]=[],documents:string[]=[];
 page.on("request",request=>{const path=new URL(request.url()).pathname;if(path.endsWith("/ancestors"))ancestors.push(path);if(/^\/api\/pages\/\d+$/.test(path))documents.push(path);});
 await page.goto(`/pages/${parentId}`);await expect(page.locator(".bn-editor")).toBeVisible();
 const breadcrumb=page.getByRole("navigation",{name:"Breadcrumb"});await expect(breadcrumb).toContainText("Ancestor 0");await expect(breadcrumb).toContainText("Ancestor 9");
 expect(ancestors).toEqual([`/api/pages/${parentId}/ancestors`]);expect(documents).toEqual([`/api/pages/${parentId}`]);
});
