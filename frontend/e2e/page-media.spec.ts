import {test,expect,type Page} from "@playwright/test";

async function pngBytes(page:Page){return Buffer.from(await page.evaluate(()=>{const canvas=document.createElement("canvas");canvas.width=canvas.height=2;const context=canvas.getContext("2d")!;context.fillStyle="#7c3aed";context.fillRect(0,0,2,2);return canvas.toDataURL("image/png").split(",")[1];}),"base64");}
async function newDocument(page:Page,type="paragraph"){
 const created=await page.request.post("/api/pages",{data:{title:"Media check "+type,content:[{type,props:{name:"",url:""}}]}});expect(created.ok()).toBeTruthy();const record=await created.json();await page.goto("/pages/"+record.id);await expect(page.locator(".bn-editor")).toBeVisible();return record.id as number;
}
async function savedMediaCount(page:Page,id:number){const record=await(await page.request.get("/api/pages/"+id)).json();return record.content.filter((block:{props?:{url?:string}})=>block.props?.url?.startsWith("/api/files/")).length as number;}

test("an uploaded page image survives a database reload",async({page})=>{
 const created=await page.request.post("/api/pages",{data:{title:"Media persistence check",content:[{type:"image",props:{name:"",url:""}}]}});expect(created.ok()).toBeTruthy();const document=await created.json();
 await page.goto("/pages/"+document.id);await page.getByText("Add image",{exact:true}).click();
 await expect(page.locator('input[type="file"]')).toHaveCount(1);
 const png=await pngBytes(page);
 let release=()=>{};const paused=new Promise<void>(resolve=>{release=resolve;});
 await page.route("**/api/pages/*/files",async route=>{await paused;await route.continue();});
 await page.locator('input[type="file"]').setInputFiles({name:"pixel.png",mimeType:"image/png",buffer:png});
 try{await expect(page.getByRole("status").filter({hasText:"Uploading file"})).toBeVisible();await expect(page.locator('input[type="file"]')).toBeDisabled();}finally{release();}
 const image=page.locator('.bn-editor img');await expect(image).toBeVisible();await expect.poll(()=>image.evaluate((el:HTMLImageElement)=>el.naturalWidth)).toBeGreaterThan(0);
 await expect(page.getByText("Saved",{exact:true})).toBeVisible();
 const saved=await(await page.request.get("/api/pages/"+document.id)).json();expect(JSON.stringify(saved.content)).toMatch(/\/api\/files\/[a-f0-9-]+\/content/);expect(JSON.stringify(saved.content)).not.toContain("blob:");
 await page.reload();await expect(image).toBeVisible();await expect.poll(()=>image.evaluate((el:HTMLImageElement)=>el.naturalWidth)).toBeGreaterThan(0);
});

test("paste and drag/drop image files persist as local references",async({page})=>{
 const id=await newDocument(page),bytes=await pngBytes(page);const errors:string[]=[];page.on("pageerror",e=>errors.push(e.message));
 for(const kind of ["paste","drop"]){
  await page.locator(".bn-editor").click();await page.locator(".bn-editor").evaluate((el,{kind,bytes})=>{
   const transfer=new DataTransfer();transfer.items.add(new File([new Uint8Array(bytes)],`${kind}.png`,{type:"image/png"}));const rect=el.getBoundingClientRect();
   el.dispatchEvent(kind==="paste"?new ClipboardEvent("paste",{clipboardData:transfer,bubbles:true,cancelable:true}):new DragEvent("drop",{dataTransfer:transfer,clientX:rect.left+30,clientY:rect.top+20,bubbles:true,cancelable:true}));
  },{kind,bytes:[...bytes]});
  await expect.poll(()=>savedMediaCount(page,id)).toBe(kind==="paste"?1:2);
 }
 await page.reload();await expect(page.locator(".bn-editor img")).toHaveCount(2);for(const img of await page.locator(".bn-editor img").all())await expect.poll(()=>img.evaluate((el:HTMLImageElement)=>el.naturalWidth)).toBeGreaterThan(0);expect(errors).toEqual([]);
});

test("unsafe pasted media shows an error and leaves the editor usable",async({page})=>{
 const id=await newDocument(page);const errors:string[]=[];page.on("pageerror",e=>errors.push(e.message));await page.locator(".bn-editor").click();
 await page.locator(".bn-editor").evaluate(el=>{const transfer=new DataTransfer();transfer.items.add(new File(["<svg onload='alert(1)'/>"],"unsafe.svg",{type:"image/svg+xml"}));el.dispatchEvent(new ClipboardEvent("paste",{clipboardData:transfer,bubbles:true,cancelable:true}));});
 await expect(page.getByRole("alert").filter({hasText:"SVG and HTML"})).toBeVisible();expect(await savedMediaCount(page,id)).toBe(0);expect(errors).toEqual([]);await expect(page.getByLabel("Page title")).toBeEditable();
});

test("video plays after upload and reload, and regular files stay downloadable",async({page})=>{
 const id=await newDocument(page,"video");
 const bytes=await page.evaluate(async()=>{
  const canvas=document.createElement("canvas");canvas.width=canvas.height=16;const context=canvas.getContext("2d")!;context.fillStyle="blue";context.fillRect(0,0,16,16);
  const stream=canvas.captureStream(10);const recorder=new MediaRecorder(stream,{mimeType:"video/webm"});const chunks:Blob[]=[];recorder.ondataavailable=e=>chunks.push(e.data);
  const finished=new Promise<Blob>(resolve=>{recorder.onstop=()=>resolve(new Blob(chunks,{type:"video/webm"}));});recorder.start();for(let frame=0;frame<6;frame++){context.fillStyle=frame%2?"red":"blue";context.fillRect(0,0,16,16);await new Promise(resolve=>setTimeout(resolve,150));}recorder.stop();const blob=await finished;stream.getTracks().forEach(track=>track.stop());return [...new Uint8Array(await blob.arrayBuffer())];
 });
 await page.getByText("Add video",{exact:true}).click();await page.locator('input[type="file"]').setInputFiles({name:"lecture.webm",mimeType:"video/webm",buffer:Buffer.from(bytes)});
 await expect.poll(()=>savedMediaCount(page,id)).toBe(1);await page.reload();const video=page.locator(".bn-editor video");await expect(video).toBeVisible();await expect.poll(()=>video.evaluate((el:HTMLVideoElement)=>el.readyState)).toBeGreaterThanOrEqual(2);
 await video.evaluate((el:HTMLVideoElement)=>el.play());await expect.poll(()=>video.evaluate((el:HTMLVideoElement)=>el.currentTime)).toBeGreaterThan(0);
 const filePage=await newDocument(page,"file");await page.getByText("Add file",{exact:true}).click();await page.locator('input[type="file"]').setInputFiles({name:"lecture.txt",mimeType:"text/plain",buffer:Buffer.from("Lecture notes")});await expect.poll(()=>savedMediaCount(page,filePage)).toBe(1);await page.reload();await expect(page.locator(".bn-editor")).toContainText("lecture.txt");
 const record=await(await page.request.get("/api/pages/"+filePage)).json();const url=record.content.find((block:{type:string})=>block.type==="file").props.url;const response=await page.request.get(url);expect(response.headers()["content-disposition"]).toContain("attachment;");expect(await response.text()).toBe("Lecture notes");
});
