import {test as base,expect} from "@playwright/test";
export const test=base.extend({page:async({page},use)=>{
 const r=await page.request.post("/api/auth/register",{data:{fullName:"Phase Test",email:"phase-"+Date.now()+"-"+Math.random()+"@example.test",password:"Local-test-password!"}});
 expect(r.status()).toBe(201);await use(page);
}});
export {expect};
