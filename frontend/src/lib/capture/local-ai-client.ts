import type {LocalAIStatus,SubtaskProvider} from "./types";

export const localSubtaskProvider:SubtaskProvider={
 id:"ollama-local",
 async detect(signal){
  try{
   const response=await fetch("/api/local-ai",{signal,cache:"no-store"});
   if(!response.ok)throw new Error();
   return await response.json() as LocalAIStatus;
  }catch{return {available:false,message:"Enable local AI: run Ollama with a downloaded local model, then check again. Task capture works without it."};}
 },
 async suggest(title,signal){
  const response=await fetch("/api/local-ai",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({title}),signal});
  const data=await response.json();
  if(!response.ok)throw new Error(data.message??"Local AI is unavailable. You can still save your task.");
  if(!Array.isArray(data.subtasks)||data.subtasks.some((s:unknown)=>typeof s!=="string"))throw new Error("Local AI returned an invalid suggestion. You can still save your task.");
  return data.subtasks;
 }
};
