"use client";
import {useRef,useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";

type Format="json"|"csv"|"markdown";
type Summary={tasks:number;documents:number;attachments:number;attachmentBytes:number;withinLimits:boolean;message:string};
const descriptions:Record<Format,string>={
 json:"Full-fidelity task and document data with stable IDs. Include attachments to preserve the files themselves.",
 csv:"Tasks for spreadsheets. Cells are quoted and formula-like text is prefixed with an apostrophe. Use JSON for exact text and document backups.",
 markdown:"Readable task checklists and document text. Rich formatting, attachments and some task fields are only preserved in JSON.",
};

export function DataExport(){
 const[format,setFormat]=useState<Format>("json"),[documents,setDocuments]=useState(true),[attachments,setAttachments]=useState(true),[archived,setArchived]=useState(false);
 const[busy,setBusy]=useState(false),[error,setError]=useState(""),[success,setSuccess]=useState("");const saving=useRef(false);
 const options={format,includeDocuments:format!=="csv"&&documents,includeAttachments:format==="json"&&attachments,includeArchived:archived};
 const params=new URLSearchParams(Object.entries(options).map(([key,value])=>[key,String(value)]));
 const summary=useQuery({queryKey:["data-export-summary",options],queryFn:({signal})=>api.get<Summary>("/data/summary?"+params,signal),retry:false,staleTime:0});
 const clearStatus=()=>{setError("");setSuccess("");};
 async function download(){
  if(saving.current)return;saving.current=true;setBusy(true);clearStatus();
  try{
   const blob=await api.download("/data/export",options);
   const name=`deadlinr-${new Date().toISOString().slice(0,10)}.${format==="markdown"?"md":format}`;
   const url=URL.createObjectURL(blob),link=document.createElement("a");link.href=url;link.download=name;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);
   setSuccess(`Download ready: ${name}`);
  }catch(e){setError(e instanceof Error?e.message:"Could not export data. Please retry.");}
  finally{saving.current=false;setBusy(false);}
 }
 return <section id="data" aria-labelledby="data-heading" className="panel p-6 grid gap-4 mt-6 max-w-xl">
  <div><h2 id="data-heading" className="font-semibold">Data</h2><p className="text-sm text-muted-foreground mt-1">Export your tasks and documents to a local file.</p></div>
  <fieldset disabled={busy} className="grid gap-4 min-w-0">
   <legend className="font-medium text-sm mb-3">Export</legend>
   <label className="field">Export format<select value={format} aria-describedby="export-description" onChange={e=>{setFormat(e.target.value as Format);clearStatus();}}><option value="json">JSON · full fidelity</option><option value="csv">CSV · tasks</option><option value="markdown">Markdown · readable</option></select></label>
   <p id="export-description" className="text-sm text-muted-foreground">{descriptions[format]}</p>
   {format!=="csv"&&<label className="flex gap-2 text-sm"><input type="checkbox" checked={documents} onChange={e=>{setDocuments(e.target.checked);clearStatus();}}/>Include documents</label>}
   {format==="json"&&<label className="flex gap-2 text-sm"><input type="checkbox" checked={attachments} onChange={e=>{setAttachments(e.target.checked);clearStatus();}}/>Include attachment files</label>}
   <label className="flex gap-2 text-sm"><input type="checkbox" checked={archived} onChange={e=>{setArchived(e.target.checked);clearStatus();}}/>Include trash</label>
  </fieldset>
  <div aria-live="polite" aria-atomic="true" className="text-sm">
   {summary.isPending||summary.isFetching?<p>Counting selected records…</p>:summary.isError?<div role="alert"><p>Could not count your data. {summary.error.message}</p><button type="button" className="btn mt-2" onClick={()=>void summary.refetch()}>Retry count</button></div>:summary.data&&<>
    <p>{summary.data.tasks} tasks · {summary.data.documents} documents{options.includeAttachments?` · ${summary.data.attachments} attachments (${(summary.data.attachmentBytes/1024/1024).toFixed(1)} MiB)`:""}</p>
    {summary.data.tasks+summary.data.documents===0?<p className="text-muted-foreground mt-1">No tasks or documents in this selection. You can still download an empty export.</p>:<p className="text-muted-foreground mt-1">{summary.data.message}</p>}
   </>}
  </div>
  <p className="text-xs text-muted-foreground">Completed tasks are included. Deadlines retain their UTC instant and saved timezone. Calendar events are excluded. Limits: 2,000 tasks and documents combined; JSON 50 MiB, CSV/Markdown 5 MiB; up to 100 attachments, 20 MiB each. Large exports fail with an explanation and are never truncated.</p>
  {error&&<p role="alert" className="text-sm text-destructive">{error}</p>}
  <p role="status" className="text-sm break-words">{success}</p>
  <button type="button" className="btn btn-primary justify-self-start" disabled={busy||summary.isFetching||!summary.data?.withinLimits||summary.isError} onClick={()=>void download()}>{busy?"Preparing download…":"Download export"}</button>
 </section>;
}
