import {test,expect} from "@playwright/test";
import {OllamaSubtaskProvider} from "../src/lib/capture/ollama";
import {GET,POST} from "../src/app/api/local-ai/route";

const model={name:"local:latest",details:{format:"gguf"}};
function mock(response:unknown,cloud=false){
 const calls:{url:string;body:unknown}[]=[];
 const fetcher:typeof fetch=async(url,init)=>{
  calls.push({url:String(url),body:init?.body?JSON.parse(String(init.body)):null});
  if(String(url).endsWith("/tags"))return Response.json({models:[model]});
  if(String(url).endsWith("/show"))return Response.json(cloud?{remote_host:"https://example.com"}:{capabilities:["completion"],model_info:{architecture:"test"}});
  return Response.json({response:JSON.stringify(response)});
 };
 return {provider:new OllamaSubtaskProvider(fetcher),calls};
}
test("missing Ollama is a normal unavailable state",async()=>{
 const provider=new OllamaSubtaskProvider(async()=>{throw new TypeError("Connection refused");});
 expect(await provider.detect()).toMatchObject({available:false});await expect(provider.suggest("Essay")).rejects.toThrow("Enable local AI");
});
test("only local models receive task text and suggestions are validated",async()=>{
 const {provider,calls}=mock({subtasks:["Read rubric","Write draft","Write draft"]});
 expect(await provider.detect()).toEqual({available:true,model:"local:latest"});
 expect(await provider.suggest("Essay")).toEqual(["Read rubric","Write draft"]);
 expect(calls.every(call=>call.url.startsWith("http://127.0.0.1:11434/"))).toBe(true);
 expect(calls.at(-1)?.body).toMatchObject({stream:false,format:"json"});
});
test("cloud-backed aliases are rejected before sending task text",async()=>{
 const {provider,calls}=mock({subtasks:["Write draft"]},true);
 await expect(provider.suggest("Private essay")).rejects.toThrow("Enable local AI");
 expect(calls.some(call=>call.url.endsWith("/generate"))).toBe(false);
});
test("cloud tags and empty lists are not detected as available",async()=>{
 for(const models of [[],[{...model,name:"test:cloud"}],[{...model,remote_host:"https://example.com"}]]){
  const provider=new OllamaSubtaskProvider(async()=>Response.json({models}));
  expect((await provider.detect()).available).toBe(false);
 }
});
test("malformed suggestions fail without affecting the parser",async()=>{
 for(const value of [null,{subtasks:[]},{subtasks:[42]},{subtasks:[""]},{subtasks:Array(9).fill("Step")}]){
  await expect(mock(value).provider.suggest("Essay")).rejects.toThrow("invalid suggestions");
 }
});
test("cancelled local-model requests fail cleanly",async()=>{
 const provider=new OllamaSubtaskProvider(async(_url,init)=>{init?.signal?.throwIfAborted();return Response.json({models:[]});});
 const controller=new AbortController();controller.abort();
 expect((await provider.detect(controller.signal)).available).toBe(false);
});
test("the local AI route rejects cross-origin and malformed requests",async()=>{
 expect((await POST(new Request("http://localhost:3000/api/local-ai",{method:"POST",headers:{origin:"https://example.com"}}))).status).toBe(403);
 expect((await GET(new Request("http://example.com/api/local-ai"))).status).toBe(200);
 for(const [body,status] of [["{}",400],["invalid json",400],[JSON.stringify({title:"x".repeat(501)}),400],[" ".repeat(4097),413]] as const){
  expect((await POST(new Request("http://localhost:3000/api/local-ai",{method:"POST",headers:{"Content-Type":"application/json"},body}))).status).toBe(status);
 }
});
