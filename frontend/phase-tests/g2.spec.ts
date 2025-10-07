import {test,expect} from "./fixtures";
test("G2 subtree delete, Undo, Trash restore and permanent delete update counts",async({page})=>{
 const make=async(title:string,parentId?:number)=>(await(await page.request.post("/api/pages",{data:{title,parentId}})).json()).id;
 const p=await make("Parent");await make("Child One",p);await make("Child Two",p);
 await page.goto("/dashboard");await expect(page.getByTestId("count-pages")).toHaveText("3");
 await page.goto("/pages/"+p);await page.getByRole("button",{name:"Delete page",exact:true}).click();
 await expect(page.getByRole("dialog")).toContainText("3 pages including 2 subpages");
 await page.getByRole("button",{name:"Confirm",exact:true}).click();
 await expect(page.locator("[data-page-id]")).toHaveCount(0);
 await page.getByRole("button",{name:"Undo",exact:true}).click();await expect(page.locator("[data-page-id]")).toHaveCount(3);
 await page.goto("/pages/"+p);await page.getByRole("button",{name:"Delete page",exact:true}).click();await page.getByRole("button",{name:"Confirm",exact:true}).click();
 await page.goto("/trash");await expect(page.locator("main").getByText("Parent",{exact:true})).toBeVisible();
 const parentRow=page.locator("main .list-row").filter({has:page.getByText("Parent",{exact:true})});
 await parentRow.getByRole("button",{name:"Restore",exact:true}).click();await expect(page.getByText("Trash is empty",{exact:true})).toBeVisible();
 await page.request.post("/api/workspace/pages/"+p+"/archive");await page.reload();
 await page.locator("main .list-row").filter({has:page.getByText("Parent",{exact:true})}).getByRole("button",{name:"Delete forever"}).click();
 await page.getByRole("button",{name:"Confirm",exact:true}).click();await expect(page.getByText("Trash is empty",{exact:true})).toBeVisible();
 await page.goto("/dashboard");await expect(page.getByTestId("count-pages")).toHaveText("0");
});
