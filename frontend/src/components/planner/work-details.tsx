"use client";
import {useState,type ReactNode} from "react";
import {useQuery} from "@tanstack/react-query";
import {ArrowUpRight,CalendarDays,ChevronRight,FileText,ListChecks,Paperclip,Pencil} from "lucide-react";
import {api,downloadFile} from "@/lib/api";
import {keys} from "@/lib/query-keys";
import type {WorkItem} from "@/lib/work";
import {formatDate,formatDateTime,formatUtcOffset} from "@/lib/datetime";
import {PriorityPill,StatusPill,useToast} from "@/components/ui/workspace";
import {Subtasks} from "./item-extras";

type FileInfo={id:string;name:string;size:number;contentType:string;uploadedAt:string};

function Property({label,children}:{label:string;children:ReactNode}){
 return <div className="item-property"><dt>{label}</dt><dd>{children}</dd></div>;
}
function reminderLabel(minutes:number){return minutes===0?"At deadline":minutes%1440===0?`${minutes/1440} day${minutes===1440?"":"s"} before`:minutes%60===0?`${minutes/60} hour${minutes===60?"":"s"} before`:`${minutes} minutes before`;}

export function WorkDetails({item,onEdit,onClose,editLabel}:{item:WorkItem;onEdit:()=>void;onClose:()=>void;editLabel:string}){
 const [subtasksOpen,setSubtasksOpen]=useState(false);
 const date=(value:string)=>`${item.allDay?formatDate(value,item.timeZone):formatDateTime(value,item.timeZone)} · ${formatUtcOffset(value,item.timeZone)}`;
 const overdue=item.status!=="DONE"&&item.dueAt!=null&&new Date(item.dueAt)<new Date();
 const repeat=({DAILY:"Daily",WEEKLY:"Weekly",MONTHLY:"Monthly",YEARLY:"Yearly"} as Record<string,string>)[item.repeatRule]??(item.repeatRule&&item.repeatRule!=="NONE"?item.repeatRule:"Does not repeat");
 return <>
  <div className="item-detail-body">
   <header className="item-detail-title">
    <span className="item-detail-kind">{item.kind==="EVENT"?<CalendarDays size={14}/>:<ListChecks size={14}/>} {item.kind==="EVENT"?"Event":"Task"} <span>#{item.id}</span></span>
    <h3>{item.title}</h3>
    <div className="flex flex-wrap gap-2"><StatusPill status={item.status}/><PriorityPill priority={item.priority}/>{item.blocked&&<span className="pill text-muted-foreground">Blocked</span>}</div>
   </header>
   {item.description&&<section className="item-detail-description" aria-label="Description"><p>{item.description}</p></section>}
   <dl className="item-properties" aria-label="Item properties">
    <Property label={item.kind==="EVENT"?"Ends":"Deadline"}>{item.dueAt?<span className={overdue?"text-destructive font-medium":""}>{overdue?"Overdue · ":""}<time dateTime={item.dueAt}>{date(item.dueAt)}</time>{item.hardDeadline&&<span className="block text-xs text-muted-foreground">Must finish on time</span>}</span>:<span className="text-muted-foreground">No date</span>}</Property>
    {item.startAt&&<Property label="Starts"><time dateTime={item.startAt}>{date(item.startAt)}</time></Property>}
    <Property label="Timezone"><span title={item.timeZone}>{formatUtcOffset(item.dueAt??item.startAt??new Date(),item.timeZone)}</span></Property>
    <Property label="Repeat">{repeat}</Property>
    {item.labels.length>0&&<Property label="Labels"><div className="flex flex-wrap gap-1">{item.labels.map(label=><span className="item-detail-label" key={label}>#{label}</span>)}</div></Property>}
    {item.reminderMinutes.length>0&&<Property label="Reminders">{item.reminderMinutes.map(reminderLabel).join(", ")}</Property>}
    {item.location&&<Property label="Location">{item.location}</Property>}
    {item.link&&<Property label="Link"><a className="underline underline-offset-2" href={/^https?:\/\//i.test(item.link)?item.link:undefined} target="_blank" rel="noreferrer">{item.link}</a></Property>}
   </dl>
   <div className="item-detail-related">
    {item.pageId!=null&&<a className="item-detail-page" href={"/pages/"+item.pageId}><FileText size={17} className="text-muted-foreground"/><span><small>Linked page</small><strong>{item.pageTitle||"Page #"+item.pageId}</strong></span><ArrowUpRight size={16} className="text-muted-foreground"/></a>}
    <DetailFiles item={item}/>
    {item.subtaskCount>0&&<details className="item-detail-section" onToggle={e=>setSubtasksOpen(e.currentTarget.open)}><summary><ListChecks size={15}/><span>Subtasks</span><span className="item-detail-count">{item.subtasksDone}/{item.subtaskCount}</span><ChevronRight size={14} className="item-detail-chevron"/></summary>{subtasksOpen&&<div className="item-detail-section-body"><Subtasks item={item}/></div>}</details>}
    {(item.createdAt||item.completedAt)&&<details className="item-detail-section"><summary><CalendarDays size={15}/><span>Activity</span><ChevronRight size={14} className="item-detail-chevron"/></summary><dl className="item-detail-section-body item-properties">{item.createdAt&&<Property label="Created"><time dateTime={item.createdAt}>{formatDateTime(item.createdAt,item.timeZone)}</time></Property>}{item.completedAt&&<Property label="Completed"><time dateTime={item.completedAt}>{formatDateTime(item.completedAt,item.timeZone)}</time></Property>}</dl></details>}
   </div>
  </div>
  <footer className="item-detail-footer"><button className="btn" onClick={onClose}>Close <kbd>Esc</kbd></button><button className="btn btn-primary" onClick={onEdit}><Pencil size={14}/>{editLabel}</button></footer>
 </>;
}

function DetailFiles({item}:{item:WorkItem}){
 const query=useQuery({queryKey:keys.work.files(item.id),queryFn:()=>api.get<FileInfo[]>("/work-items/"+item.id+"/files")});
 const toast=useToast();const [downloading,setDownloading]=useState<string|null>(null);
 if(!query.isError&&!(query.data?.length??item.fileCount))return null;
 return <section className="item-detail-section" aria-label="Files"><div className="item-detail-section-heading"><Paperclip size={15}/><h4>Files</h4><span className="item-detail-count">{query.data?.length??item.fileCount}</span></div>
  {query.isPending?<div className="item-detail-section-body" role="status" aria-label="Loading files"><div className="skeleton h-10 rounded-md"/></div>:query.isError?<div className="item-detail-section-body"><p role="alert" className="text-sm">Could not load files.</p><button className="btn mt-2" onClick={()=>void query.refetch()}>Retry files</button></div>:query.data?.map(file=><div className="item-detail-file" key={file.id}><FileText size={17} className="text-muted-foreground"/><div><p title={file.name}>{file.name}</p><span>{(file.size/1024).toFixed(1)} KB</span></div><button className="btn" disabled={downloading!==null} aria-label={"Download "+file.name} onClick={async()=>{setDownloading(file.id);try{await downloadFile(file.id,file.name);}catch(e){toast({message:e instanceof Error?e.message:"Could not download file"});}finally{setDownloading(null);}}}>{downloading===file.id?"Downloading…":"Download"}</button></div>)}
 </section>;
}
