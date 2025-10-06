"use client";
import {useState,useRef} from "react";
import {useQuery,useQueryClient} from "@tanstack/react-query";
import {api} from "@/lib/api";
import type {WorkInput,WorkItem,WorkKind} from "@/lib/work";
import {DeadlinePicker} from "./date-picker";
import {useRefreshWorkspace} from "@/hooks/use-workspace";
import {keys} from "@/lib/query-keys";
import {useToast} from "@/components/ui/workspace";
export function WorkForm({initial={},item,onSaved,onCancel,dirtyRef:dirtyRefProp,createAnother=false,onCreateAnother}:{initial?:WorkInput;item?:WorkItem;onSaved:(w:WorkItem)=>void;onCancel?:()=>void;dirtyRef?:React.MutableRefObject<boolean>;createAnother?:boolean;onCreateAnother?:(v:boolean)=>void}){
 const [form,setForm]=useState<WorkInput>(()=>({kind:"TASK",title:"",description:"",status:"TODO",priority:3,dueAt:null,startAt:null,allDay:false,hardDeadline:false,labels:[],reminderMinutes:[60],repeatRule:"NONE",timeZone:Intl.DateTimeFormat().resolvedOptions().timeZone,...initial,...item}));
 const localDirtyRef=useRef(false);
 const dirtyRef=dirtyRefProp??localDirtyRef;
 const [labelText,setLabelText]=useState((item?.labels??initial.labels??[]).join(", "));
 const [files,setFiles]=useState<File[]>([]),[createdId,setCreatedId]=useState<number|null>(null);
 const [busy,setBusy]=useState(false),[error,setError]=useState("");

 const pages=useQuery({queryKey:keys.pages.list(false,0),queryFn:()=>api.get<{id:number;title:string}[]>("/workspace/pages?trash=false&size=200")});
 const [pageTitle,setPageTitle]=useState<string>(item?.pageTitle??"");
 const refresh=useRefreshWorkspace(),toast=useToast(),cache=useQueryClient();
 const set=<K extends keyof WorkInput>(key:K,value:WorkInput[K])=>{setForm(f=>({...f,[key]:value}));dirtyRef.current=true;};
 const inFlight=useRef(false);
 async function submit(e:React.FormEvent){e.preventDefault();if(inFlight.current)return;inFlight.current=true;setBusy(true);setError("");
  const allowed=["kind","title","description","status","priority","dueAt","startAt","allDay","hardDeadline","labels","reminderMinutes","repeatRule","timeZone","pageId","parentId","location","link","version","position"] as const;
  type Key=typeof allowed[number];
  const entries:[string,unknown][]=allowed.filter(k=>form[k]!==undefined).map(k=>[k,form[k]] as [string,unknown]);
  const body={...Object.fromEntries(entries),labels:labelText.split(",").map(s=>s.trim()).filter(Boolean)};
  try{const saved=item||createdId?await api.patch<WorkItem>("/work-items/"+(item?.id??createdId),body):await api.post<WorkItem>("/work-items",body);setCreatedId(saved.id);for(const file of files){await api.upload("/work-items/"+saved.id+"/files",file);setFiles(left=>left.filter(f=>f!==file));}await Promise.all([refresh(),cache.invalidateQueries({queryKey:keys.labels()})]);toast({message:item?"Changes saved":"Created "+saved.kind.toLowerCase()});onSaved(saved);dirtyRef.current=false;}catch(e){setError(e instanceof Error?e.message:"Could not save");}finally{setBusy(false);inFlight.current=false;}
 }
 return <form onSubmit={submit} className="grid gap-4" onKeyDown={e=>{if(e.key==="Enter"&&(e.ctrlKey||e.metaKey)){e.preventDefault();e.currentTarget.requestSubmit();}}}>{error&&<p role="alert" className="text-sm text-destructive">{error}</p>}
 {item?.blocked&&<p className="text-sm text-muted-foreground">🔒 Blocked by unfinished work. Review the Planning tab.</p>}
 <label className="field">Title<input autoFocus aria-label="Title" required maxLength={500} placeholder="What needs your attention?" value={form.title??""} onChange={e=>set("title",e.target.value)}/></label>
 <label className="field">Description<textarea aria-label="Description" rows={3} value={form.description??""} onChange={e=>set("description",e.target.value)}/></label>
  <div className="form-grid"><div className="field"><span>Page</span><div className="flex items-center gap-2"><input aria-label="Page" list="page-options" placeholder="Search pages" value={pageTitle} onChange={e=>{setPageTitle(e.target.value);dirtyRef.current=true;const match=pages.data?.find(p=>p.title===e.target.value);set("pageId",match?match.id:null);}}/>{form.pageId!=null&&<a href={"/pages/"+form.pageId} target="_blank" rel="noreferrer" className="text-xs text-primary">Open page</a>}{form.pageId==null&&pageTitle&&<button type="button" className="text-xs text-muted-foreground" onClick={()=>{setPageTitle("");set("pageId",null);}}>Clear</button>}</div><datalist id="page-options">{pages.data?.map(p=><option key={p.id} value={p.title}/>)}</datalist></div>
  {/* course selector removed: use labels to group tasks */}
 {(form.kind==="EVENT")&&<div className="field"><span>Starts</span><DeadlinePicker label="Start date" value={form.startAt??null} onChange={v=>set("startAt",v)} allDay={form.allDay}/></div>}
 <div className="field"><span>{form.kind==="EVENT"?"Ends":"Deadline"}</span><DeadlinePicker value={form.dueAt??null} onChange={v=>set("dueAt",v)} allDay={form.allDay} onAllDay={v=>set("allDay",v)}/></div>
 <label className="field">Priority<select aria-label="Priority" value={form.priority} onChange={e=>set("priority",Number(e.target.value))}>{["Urgent","High","Medium","Low"].map((label,i)=><option key={label} value={i+1}>P{i+1} {label}</option>)}</select></label>
 <label className="field">Status<select aria-label="Status" value={form.status} onChange={e=>set("status",e.target.value as WorkItem["status"])}><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option><option value="SOMEDAY">Someday</option></select></label>
 <label className="field">Repeat<select value={form.repeatRule} onChange={e=>set("repeatRule",e.target.value)}>{["NONE","DAILY","WEEKLY","MONTHLY","YEARLY"].map(k=><option key={k}>{k}</option>)}</select></label></div>
 <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.hardDeadline??false} onChange={e=>set("hardDeadline",e.target.checked)}/>Must finish on time</label>
 <label className="field">Labels<input aria-label="Labels" placeholder="ml, university" value={labelText} onChange={e=>setLabelText(e.target.value)}/></label>
 {(form.kind==="EVENT")&&<div className="form-grid"><label className="field">Location<input value={form.location??""} onChange={e=>set("location",e.target.value)}/></label><label className="field">Link<input type="url" value={form.link??""} onChange={e=>set("link",e.target.value)}/></label></div>}
 <fieldset><legend className="mb-2 text-sm font-medium">Remind me</legend><div className="flex flex-wrap gap-3">{[[1440,"1 day before"],[60,"1 hour before"],[10,"10 minutes before"],[0,"At deadline"]].map(([minutes,label])=><label key={minutes} className="flex items-center gap-2 text-xs"><input type="checkbox" checked={form.reminderMinutes?.includes(Number(minutes))??false} onChange={e=>set("reminderMinutes",e.target.checked?[...(form.reminderMinutes??[]),Number(minutes)]:(form.reminderMinutes??[]).filter(m=>m!==Number(minutes)))}/>{label}</label>)}</div></fieldset>
 {!item&&<label className="field">Attachments (up to 20 MB each)<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.gif,.txt,.csv,.zip,.docx,.xlsx,.pptx" onChange={e=>{setFiles(Array.from(e.target.files??[]));dirtyRef.current=true;}}/></label>}
 <div className="mt-2 flex justify-end gap-2">{onCancel&&<button type="button" className="btn" onClick={onCancel}>Cancel</button>}<button type="submit" disabled={busy} className="btn btn-primary">{busy?"Saving…":item?"Save changes":"Create "+(form.kind??"task").toLowerCase()}</button></div>
 {onCancel&&!item&&<label className="flex items-center gap-2 text-sm mt-2"><input type="checkbox" checked={createAnother} onChange={e=>onCreateAnother?.(e.target.checked)}/>Create another</label>}
</form>;
}











