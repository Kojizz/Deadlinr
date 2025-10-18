"use client";
import {keys} from "@/lib/query-keys";
import {useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {api,downloadFile} from "@/lib/api";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {useWorkList,useWorkUpdate} from "@/hooks/use-work";
import {openWork,type WorkItem} from "@/lib/work";
import {useToast,ConfirmDialog} from "@/components/ui/workspace";
import {formatDate,formatDateTime} from "@/lib/datetime";
type FileInfo={id:string;name:string;size:number;contentType:string;uploadedAt:string};
export function FilesPanel({id}:{id:number}){
 const files=useQuery({queryKey:keys.work.files(id),queryFn:()=>api.get<FileInfo[]>("/work-items/"+id+"/files")}),refresh=useRefreshWorkspace(),toast=useToast();const[busy,setBusy]=useState(false),[remove,setRemove]=useState<string|null>(null);
 return <div className="grid gap-4"><label className="field">Attach files<input aria-label="Attach files" disabled={busy} type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.gif,.txt,.csv,.zip,.docx,.xlsx,.pptx" onChange={async e=>{const input=e.currentTarget;setBusy(true);try{for(const f of Array.from(input.files??[])){await api.upload("/work-items/"+id+"/files",f);}await refresh();toast({message:"Files attached"});}catch(e){toast({message:String(e)});}finally{setBusy(false);input.value="";}}}/></label><p className="text-xs text-muted-foreground">Up to 20 MB per file. PDF, images, text, CSV, ZIP and Office documents.</p>
 {files.isError&&<p role="alert">Could not load files.</p>}{files.data?.map(f=><div className="list-row" key={f.id}><div className="flex-1 min-w-0"><p className="truncate text-sm font-medium">{f.name}</p><p className="text-xs text-muted-foreground">{(f.size/1024).toFixed(1)} KB · {formatDate(f.uploadedAt)}</p></div><button className="btn" onClick={()=>void downloadFile(f.id,f.name).catch(e=>toast({message:String(e)}))}>Download</button><button className="btn btn-danger" onClick={()=>setRemove(f.id)}>Remove</button></div>)}
 <ConfirmDialog open={!!remove} title="Remove file permanently?" description="This deletes the attachment from this item and local storage." onClose={()=>setRemove(null)} onConfirm={async()=>{await api.del("/files/"+remove);await refresh();setRemove(null);}}/></div>;
}
export function Subtasks({item}:{item:WorkItem}){
 const list=useWorkList({parentId:String(item.id),size:"200"}),update=useWorkUpdate(),refresh=useRefreshWorkspace(),toast=useToast();const[title,setTitle]=useState("");
 return <div className="grid gap-3">{list.data?.items.map(w=><div className="list-row" key={w.id}><input type="checkbox" aria-label={"Complete "+w.title} checked={w.status==="DONE"} onChange={e=>update.mutate({id:w.id,patch:{status:e.target.checked?"DONE":"TODO",version:w.version}})}/><button className="text-left flex-1" onClick={()=>openWork(w.id)}>{w.title}</button></div>)}<form className="flex gap-2" onSubmit={async e=>{e.preventDefault();try{await api.post("/work-items",{title,parentId:item.id,kind:"TASK"});setTitle("");await refresh();}catch(e){toast({message:String(e)});}}}><input className="input" aria-label="Add subtask" required value={title} onChange={e=>setTitle(e.target.value)} placeholder="Add subtask"/><button className="btn">Add</button></form></div>;
}
export function Reminders({id}:{id:number}){
 const query=useQuery({queryKey:keys.work.reminders(id),queryFn:()=>api.get<{id:number;scheduledAt:string;sentAt:string|null;cancelled:boolean;category:string}[]>("/work-items/"+id+"/reminders")});
 return <div className="grid gap-2">{query.isError?<p role="alert">Could not load reminders.</p>:query.data?.length?query.data.map(r=><div className="list-row" key={r.id}><time className="flex-1 text-sm">{formatDateTime(r.scheduledAt)}</time><span className="text-xs">{r.category} · {r.cancelled?"Cancelled":r.sentAt?"Sent":"Scheduled"}</span></div>):<p className="text-sm text-muted-foreground">No reminders scheduled. Set a deadline and reminder in Details.</p>}</div>;
}








